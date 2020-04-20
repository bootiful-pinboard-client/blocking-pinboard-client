package pinboard.resttemplate

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.web.client.RestTemplate
import pinboard.Bookmark
import pinboard.PinboardClient
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.*
import kotlin.Comparator


class PinboardClientTest {

	private val pinboardClient = PinboardClient(
			System.getenv("PINBOARD_TOKEN"), RestTemplate())

	private val testTag = "pbctest"

	private val testTag2 = "pbctest2"

	private val bookmark = Bookmark("http://garfield.com", "description", "extended", "hash", "meta",
			Date(), true, true, arrayOf(this.testTag, this.testTag2))

	private val pinboardClientTestTag: Array<String> by lazy {
		bookmark.tags
	}

	@BeforeEach
	fun setUp() {
		addTestPost()
	}

	private fun deleteTestPosts() {
		pinboardClient.getAllPosts(tag = pinboardClientTestTag).forEach { pinboardClient.deletePost(it.href!!) }
	}

	private fun addTestPost(): String {
		val post = pinboardClient.addPost(bookmark.href!!, bookmark.description!!, bookmark.extended!!, bookmark.tags, bookmark.time!!, true, true, true)
		Assertions.assertNotNull(post) { "the bookmark has not been added." }
		return bookmark.href!!
	}

	@AfterEach
	fun cleanup() {
		deleteTestPosts()
	}

	@Test
	fun getPosts() {
		val result = pinboardClient.getPosts(bookmark.href)
		val href = result.posts.first().href
		assertTrue(href == bookmark.href)
	}

	@Test
	fun getAllBookmarksByTag() {
		val postsByTag = pinboardClient.getAllPosts(arrayOf("twis"))
		assertTrue(postsByTag.isNotEmpty())
		assertTrue(postsByTag.size > 1)
	}

	@Test
	fun get10Records() {
		val maxResults = 10
		val postsByTag = pinboardClient.getAllPosts(arrayOf("twis"), 0, maxResults)
		assertEquals(postsByTag.size, maxResults) { "there should be no more than 10 getAllPosts." }
	}

	@Test
	fun getTheLastTenDays() {
		val fromdt = Date.from(Instant.now().minus(10, ChronoUnit.DAYS))
		val postsByDate = pinboardClient.getAllPosts(arrayOf("twis"), fromdt = fromdt)
		val comparator = Comparator<Bookmark> { a, b -> a.time!!.compareTo(b.time) }
		val minBookmark = postsByDate.minWith(comparator)
		val value = minBookmark!!.time!!.after(fromdt)
		assertTrue(value)
	}

	@Test
	fun deletePost() {
		assertTrue(bookmark.href != null)
		assertTrue(pinboardClient.getAllPosts(bookmark.tags).size == 1)
		pinboardClient.deletePost(bookmark.href!!)
		assertTrue(pinboardClient.getAllPosts(bookmark.tags).isEmpty())
		pinboardClient.getAllPosts(pinboardClientTestTag).forEach { pinboardClient.deletePost(it.href!!) }
	}

	@Test
	fun addPost() {
		addTestPost()
		val posts = pinboardClient.getAllPosts(bookmark.tags)
		assertTrue(posts.size == 1) { "there should be one result that matches." }
	}

	@Test
	fun getRecentPostsByTag() {
		addTestPost()
		val result = pinboardClient.getRecentPosts(tag = bookmark.tags)
		val href = result.posts.first().href
		assertTrue(href == bookmark.href)
	}

	@Test
	fun getRecentPosts() {
		val someUrl = "http://some-url.com/"
		(0..5).forEach { i ->
			pinboardClient.addPost(someUrl + i, "description", "extended",
					bookmark.tags, Date(), replace = true, shared = true, toread = true)
		}
		val result = pinboardClient.getRecentPosts(tag = bookmark.tags)
		assertTrue(result.posts.size == 7)
		assertTrue(result.posts.any { it.href == "${someUrl}1" })
	}

	@Test
	fun getNoOfPostsByDate() {
		val tagName = "twis"
		val result = pinboardClient.getCountOfPostsByDate(arrayOf(tagName))
		assertTrue(result.user!!.toLowerCase() == "starbuxman")
		assertTrue(result.dates!!.isNotEmpty())
		assertTrue(result.tag!!.contains(tagName))
	}

	@Test
	fun suggestTagsForPost() {
		val url = listOf("https://www.washingtonpost.com/world/national-security/",
				"you-cannot-say-that-to-the-press-trump-urged-mexican-president-",
				"to-end-his-public-defiance-on-border-wall-transcript-reveals/2017/",
				"08/03/0c2c0a4e-7610-11e7-8f39-eeb7d3a2d304_story.html?utm_term=.ea1119248010")
				.joinToString("")
		val tagsForPost = pinboardClient.suggestTagsForPost(url)
		assertTrue(tagsForPost.recommended!!.isNotEmpty())
	}

	@Test
	fun tagFrequencyTable() {
		val tagCloud = pinboardClient.getUserTags()
		val twisCount = tagCloud["twis"]!!
		assertTrue(twisCount > 0)
	}

	@Test
	fun deleteTag() {
		assertTrue(pinboardClient.deleteTag(this.pinboardClientTestTag.first()))
	}

	@Test
	fun secret() {
		val userSecret = pinboardClient.getUserSecret()
		assertTrue(userSecret.isNotBlank()) { "the userSecret should not be null" }
	}

	@Test
	fun apiToken() {
		val token = pinboardClient.getApiToken()
		assertTrue(token.isNotBlank()) { "the token should not be null" }
	}

	@Test
	fun notes() {
		val userNotes = pinboardClient.getUserNotes()
		assertTrue(userNotes.count == userNotes.notes!!.size)
		val firstNote = userNotes.notes!![0]
		val firstId = firstNote.id
		val userNote = pinboardClient.getUserNote(firstId!!)
		assertTrue(userNote.id == firstId)
		assertTrue(userNote.created == firstNote.created)
		assertTrue(userNote.updated == firstNote.updated)
		assertTrue(userNote.length == firstNote.length)
		assertTrue(userNote.title == firstNote.title)
	}


}