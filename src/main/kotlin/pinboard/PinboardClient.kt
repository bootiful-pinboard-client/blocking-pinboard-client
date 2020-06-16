package pinboard

import org.springframework.boot.web.client.RestTemplateBuilder
import org.springframework.boot.web.client.RestTemplateCustomizer
import org.springframework.core.ParameterizedTypeReference
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.http.converter.json.AbstractJackson2HttpMessageConverter
import org.springframework.util.Assert
import org.springframework.web.client.RestTemplate
import pinboard.ParamUtils.exchangeParameters

import java.util.*
import kotlin.collections.HashMap

/**
 * This is a client for <a href="https://pinboard.in/api">the Pinboard API</a>.
 *
 * @author <a href="mailto:josh@joshlong.com">Josh Long</a>
 */
open class PinboardClient(
		private val token: String,
		private val restTemplateParameter: RestTemplate) {

	private val restTemplate: RestTemplate = RestTemplateBuilder()
			.additionalCustomizers(RestTemplateCustomizer {
				val messageConverter = it.messageConverters.find { it is AbstractJackson2HttpMessageConverter }
						as AbstractJackson2HttpMessageConverter
				val mts = messageConverter.supportedMediaTypes
				val listOfMts = ArrayList<MediaType>()
				listOfMts.addAll(mts)
				listOfMts.add( MediaType.parseMediaType("text/plain;charset=utf-8"))
				listOfMts.add( MediaType.parseMediaType("text/json;charset=utf-8"))
				messageConverter.supportedMediaTypes = listOfMts
			})
			.configure(this.restTemplateParameter)

	private val pinboardApiEndpoint = "https://api.pinboard.in/v1"

	open fun updatePost(post: Bookmark) =
			updatePost(
					url = post.href!!,
					description = post.description!!,
					extended = post.extended!!,
					tags = post.tags,
					dt = post.time!!,
					toread = post.toread,
					shared = post.shared
			)

	open fun updatePost(
			url: String,
			description: String,
			extended: String,
			tags: Array<String>,
			dt: Date,
			shared: Boolean,
			toread: Boolean
	): Boolean =
			this.addPost(url = url, description = description, replace = true, extended = extended, tags = tags, dt = dt, shared = shared, toread = toread)

	open fun addPost(url: String,
	                 description: String,
	                 extended: String,
	                 tags: Array<String>,
	                 dt: Date,
	                 replace: Boolean,
	                 shared: Boolean,
	                 toread: Boolean): Boolean {
		val params = exchangeParameters(token, "url" to url, "description" to description, "extended" to extended,
				"tags" to tags, "dt" to dt, "replace" to replace, "shared" to shared, "toread" to toread)
		val result: ResponseEntity<String> = exchange("/posts/add", params, object : ParameterizedTypeReference<String>() {})
		return isDone(result)
	}

	open fun deletePost(url: String): Boolean {
		val params = exchangeParameters(token, "url" to url)
		return isDone(exchange("/posts/delete", params, object : ParameterizedTypeReference<String>() {}))
	}

	open fun getAllPosts(tag: Array<String>,
	                     start: Int = 0,
	                     results: Int = -1,
	                     fromdt: Date? = null,
	                     todt: Date? = null,
	                     meta: Int = 0): Array<Bookmark> {
		Assert.isTrue(tag.size <= 3) { "there should be no more than three tag" }
		Assert.isTrue(tag.isNotEmpty()) { "there should be at least one tag" }
		val paramMap = exchangeParameters(token, "tag" to tag, "start" to start, "results" to results, "fromdt" to fromdt, "todt" to todt, "meta" to meta)
		return exchange("/posts/all", paramMap, object : ParameterizedTypeReference<Array<Bookmark>>() {}).body!!
	}

	open fun getPosts(url: String? = null, dt: Date? = null, tag: Array<String>? = null, meta: Boolean? = null): Bookmarks {
		assert(dt != null || url != null) { "you must specify either the date or the URL" }
		val parameters = exchangeParameters(token,
				"tag" to tag,
				"url" to url,
				"meta" to meta,
				"dt" to dt)
		return exchange("/posts/get", parameters, object : ParameterizedTypeReference<Bookmarks>() {}).body!!
	}

	open fun getRecentPosts(tag: Array<String>? = null, count: Int? = 15): Bookmarks {
		val parameters = exchangeParameters(token, "tag" to tag, "count" to count)
		return exchange("/posts/recent", parameters, object : ParameterizedTypeReference<Bookmarks>() {}).body!!
	}

	open fun getCountOfPostsByDate(tag: Array<String>): PostsByDate {
		val params = exchangeParameters(token, "tag" to tag)
		return exchange("/posts/dates", params, object : ParameterizedTypeReference<PostsByDate>() {}).body!!
	}

	@Deprecated(
			message = "use getCountOfPostsByDate instead",
			replaceWith = ReplaceWith("getCountOfPostsByDate(tag)")
	)
	open fun getNoOfPostsByDate(tag: Array<String>): PostsByDate {
		return getCountOfPostsByDate(tag)
	}

	open fun suggestTagsForPost(url: String): SuggestedTags {
		val params = exchangeParameters(token, "url" to url)
		val result: ResponseEntity<Array<Map<String, Array<String>>>> = exchange("/posts/suggest", params, object : ParameterizedTypeReference<Array<Map<String, Array<String>>>>() {})
		val body = result.body!!
		val popular = "popular"
		val recommended = "recommended"
		val popularStrings = body.first { it.containsKey(popular) }[popular]
		val recommendedStrings = body.first { it.containsKey(recommended) }[recommended]
		return SuggestedTags(popularStrings, recommendedStrings)
	}

	open fun getUserTags(): Map<String, Int> {
		return exchange("/tags/get", exchangeParameters(token), object : ParameterizedTypeReference<Map<String, Int>>() {}).body!!
	}

	private inline fun <reified T> exchange(incomingUrl: String,
	                                        params: Map<String, String>,
	                                        ptr: ParameterizedTypeReference<T>): ResponseEntity<T> {

		val map = params.entries.map { "${it.key}={${it.key}}" }
		val paramString = map.joinToString("&")
		val url = "${pinboardApiEndpoint}${incomingUrl}?${paramString}"
		return restTemplate.exchange(url, HttpMethod.GET, null, ptr, params)
	}

	open fun deleteTag(s: String): Boolean {
		val result = exchange("/tags/delete", exchangeParameters(token, "tag" to arrayOf(s)), object : ParameterizedTypeReference<String>() {}).body!!
		return (result.toLowerCase().contains("done"))
	}

	open fun getUserSecret(): String {
		return exchange("/user/secret", exchangeParameters(token), object : ParameterizedTypeReference<Map<String, String>>() {}).body!!["result"]!!
	}

	open fun getApiToken(): String {
		return exchange("/user/api_token/", exchangeParameters(token), object : ParameterizedTypeReference<Map<String, String>>() {}).body!!["result"]!!
	}

	open fun getUserNotes(): Notes {
		return exchange("/notes/list", exchangeParameters(token), object : ParameterizedTypeReference<Notes>() {}).body!!
	}

	open fun getUserNote(id: String): Note {
		return exchange("/notes/$id", exchangeParameters(token), object : ParameterizedTypeReference<Note>() {}).body!!
	}

	private fun defaultParameters(parmMap: Map<String, Any?>): Map<String, Any?> {
		val mix = HashMap<String, Any?>()
		mix.putAll(parmMap)
		return mix
	}

	private fun isDone(result: ResponseEntity<String>): Boolean {
		return result.body!!.contains("done") && result.statusCode == HttpStatus.OK
	}

}

