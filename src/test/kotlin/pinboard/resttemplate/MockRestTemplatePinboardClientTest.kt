package pinboard.resttemplate

import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.test.autoconfigure.json.AutoConfigureJsonTesters
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.HttpMethod
import org.springframework.http.MediaType
import org.springframework.test.web.client.ExpectedCount
import org.springframework.test.web.client.ExpectedCount.manyTimes
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.test.web.client.ResponseCreator
import org.springframework.test.web.client.match.MockRestRequestMatchers.method
import org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo
import org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess
import org.springframework.web.client.RestTemplate
import pinboard.Bookmark
import pinboard.format.FormatUtils
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoField
import java.time.temporal.ChronoUnit
import java.util.*

/**
 * @author <a href="mailto:josh@joshlong.com">Josh Long</a>
 */
@SpringBootTest(
		classes = [MockRestTemplatePinboardClientTest.Config::class],
		properties = ["pinboard.token=1234"]
)
@AutoConfigureJsonTesters
class MockRestTemplatePinboardClientTest(
		@Autowired val restTemplatePinboardClient: RestTemplatePinboardClient) {

	@SpringBootApplication
	class Config

	private val restTemplate = RestTemplate()

	private var mockRestServiceServer: MockRestServiceServer? = null
	private val auth = "1234"
	private val commonUriParams = """ format=json&auth_token=${auth}   """.trim()
	private val testTag = "pbctest"
	private val testTag2 = "pbctest2"

	private val bookmark = Bookmark("http:/" +
			"/garfield.com", "description", "extended", "hash", "meta",
			Date(), true, true, arrayOf(this.testTag, this.testTag2))

	private val pinboardClientTestTag: Array<String> by lazy {
		bookmark.tags
	}

	@BeforeEach
	fun setUp() {
		this.mockRestServiceServer = MockRestServiceServer.bindTo(this.restTemplate).build()
	}

	@Test
	fun getTheLast10Days() {
		val tenDaysAgo = Instant.now().minus(10, ChronoUnit.DAYS)
		val fromdt = Date.from(tenDaysAgo)
		val moreRecent = tenDaysAgo.plus(2, ChronoUnit.DAYS).atZone(ZoneId.systemDefault())
		val month = moreRecent.get(ChronoField.MONTH_OF_YEAR)
		val date = moreRecent.get(ChronoField.DAY_OF_MONTH)
		val year = moreRecent.get(ChronoField.YEAR_OF_ERA)
		val json =
				"""
            [
                    {"tags":"pbctest pbctest2",
                    "time":"${year}-${month}-${date}T08:21:11Z","shared":"yes","toread":"yes",
                        "href":"http://garfield.com","description":"description","extended":"extended","hash":"hash","meta":"meta"}
            ]
         """
		mockReply("/posts/all?fromdt=${FormatUtils.encodeDate(fromdt)}&meta=0&format=json&start=0&tag=twis&auth_token=$auth&results=-1", json)
		val postsByDate = restTemplatePinboardClient.getAllPosts(arrayOf("twis"), fromdt = fromdt)
		val comparator = Comparator<Bookmark> { a, b -> a.time!!.compareTo(b.time) }
		val minBookmark = postsByDate.minWith(comparator)
		assert(minBookmark!!.time!!.after(fromdt))
	}


	@Test
	fun addPost() {

		val encodeDate = FormatUtils.encodeDate(bookmark.time!!)
		mockReply("""
            /posts/add?dt=$encodeDate&shared=yes&toread=yes&format=json&replace=yes&description=description&auth_token=$auth&url=http://garfield.com&extended=extended&tags=pbctest%20pbctest2 """
				, """  { "status" : "done"}  """)
		val post = restTemplatePinboardClient.addPost(bookmark.href!!, bookmark.description!!, bookmark.extended!!, bookmark.tags, bookmark.time!!, true, true, true)
		assert(post) { "the bookmark has not been added." }
	}


	@Test
	fun getPosts() {
		val json =
				"""
            {
                     "user" : "starbuxman" ,
                     "date" : "${FormatUtils.encodeDate(Date())}",
                     "posts":
                        [
                                {"tags":"pbctest pbctest2","time":"2017-08-16T08:21:11Z","shared":"yes","toread":"yes",
                                    "href":"http://garfield.com","description":"description","extended":"extended","hash":"hash","meta":"meta"}

                        ]
            }
         """
		mockReply("/posts/get?auth_token=$auth&format=json&&url=http://garfield.com", json)

		val result = restTemplatePinboardClient.getPosts(bookmark.href)
		val href = result.posts.first().href
		assert(href == bookmark.href)
	}

	@Test
	fun getRecentPostsByTag() {
		val json =
				"""
            {
                     "user" : "starbuxman" ,
                     "date" : "${FormatUtils.encodeDate(Date())}",
                     "posts":
                        [
                                {"tags":"pbctest pbctest2","time":"2017-08-16T08:21:11Z","shared":"yes","toread":"yes",
                                    "href":"http://garfield.com","description":"description","extended":"extended","hash":"hash","meta":"meta"}

                        ]
            }
         """
		val uri = """ /posts/recent?format=json&count=15&tag=pbctest%20pbctest2&auth_token=$auth  """
		mockReply(uri, json)
		val result = restTemplatePinboardClient.getRecentPosts(tag = bookmark.tags)
		val href = result.posts.first().href
		assert(href == bookmark.href)
		mockRestServiceServer!!.verify()
	}

	@Test
	fun deletePost() {
		val json =
				"""
            [
              {"tags":"pbctest pbctest2","time":"2017-08-16T08:21:11Z","shared":"yes","toread":"yes","href":"http://garfield.com/b","description":"description","extended":"extended","hash":"hash","meta":"meta"}
            ]
        """
		mockReply("/posts/all?meta=0&format=json&start=0&tag=pbctest%20pbctest2&auth_token=$auth&results=-1", json)
		assert(bookmark.href != null)
		assert(this.restTemplatePinboardClient.getAllPosts(bookmark.tags).size == 1)
	}

	@Test
	fun getNoOfPostsByDate() {

		val json = """{"tag":"twis cats","user":"starbuxman","dates":{"2017-08-16T08:31:25.755+0000":5}} """
		mockReply("/posts/dates?format=json&tag=twis&auth_token=$auth", json)

		val tagName = "twis"
		val result = restTemplatePinboardClient.getCountOfPostsByDate(arrayOf(tagName))
		assert(result.user!!.toLowerCase() == "starbuxman")
		assert(result.dates!!.isNotEmpty())
		assert(result.tag!!.contains(tagName))

		mockRestServiceServer!!.verify()
	}

	@Test
	fun deleteTag() {
		val first = this.pinboardClientTestTag.first()
		val uri = """
         tags/delete?format=json&tag=${first}&auth_token=${auth}
        """
		mockReply(uri, """ { "status" : "done" } """)
		assert(restTemplatePinboardClient.deleteTag(first))
	}

	@Test
	fun secret() {
		mockReply("/user/secret?format=json&auth_token=${auth}", """  {  "result" : "1234" } """)
		val userSecret = restTemplatePinboardClient.getUserSecret()
		assert(userSecret.isNotBlank(), { "the userSecret should not be null" })
	}

	@Test
	fun apiToken() {
		mockReply("/user/api_token/?format=json&auth_token=${auth}", """ { "result" : "${auth}" }  """)
		val token = restTemplatePinboardClient.getApiToken()
		assert(token.isNotBlank(), { "the token should not be null" })
	}

	@Test
	fun getAllBookmarksByTag() {
		val twisTag = "twis"
		val json =
				"""
        [
            {"tags":"pbctest pbctest2","time":"2017-08-16T08:21:11Z","shared":"yes","toread":"yes","href":"http://garfield.com/a","description":"description","extended":"extended","hash":"hash","meta":"meta"},
            {"tags":"pbctest pbctest2","time":"2017-08-16T08:21:11Z","shared":"yes","toread":"yes","href":"http://garfield.com/b","description":"description","extended":"extended","hash":"hash","meta":"meta"}
        ]
        """
		val uri = """  /posts/all?meta=0&format=json&start=0&tag=${twisTag}&auth_token=${auth}&results=-1  """.trimIndent()
		mockReply(uri, json)

		val postsByTag = restTemplatePinboardClient.getAllPosts(arrayOf(twisTag))
		assert(postsByTag.isNotEmpty())
		assert(postsByTag.size > 1)
	}

	@Test
	fun get10Records() {
		val json =
				"""
        [
            {"tags":"pbctest pbctest2","time":"2017-08-16T08:21:11Z","shared":"yes","toread":"yes","href":"http://garfield.com","description":"description","extended":"extended","hash":"hash","meta":"meta"},
            {"tags":"pbctest pbctest2","time":"2017-08-16T08:21:11Z","shared":"yes","toread":"yes","href":"http://garfield.com","description":"description","extended":"extended","hash":"hash","meta":"meta"},
            {"tags":"pbctest pbctest2","time":"2017-08-16T08:21:11Z","shared":"yes","toread":"yes","href":"http://garfield.com","description":"description","extended":"extended","hash":"hash","meta":"meta"},
            {"tags":"pbctest pbctest2","time":"2017-08-16T08:21:11Z","shared":"yes","toread":"yes","href":"http://garfield.com","description":"description","extended":"extended","hash":"hash","meta":"meta"},
            {"tags":"pbctest pbctest2","time":"2017-08-16T08:21:11Z","shared":"yes","toread":"yes","href":"http://garfield.com","description":"description","extended":"extended","hash":"hash","meta":"meta"},
            {"tags":"pbctest pbctest2","time":"2017-08-16T08:21:11Z","shared":"yes","toread":"yes","href":"http://garfield.com","description":"description","extended":"extended","hash":"hash","meta":"meta"},
            {"tags":"pbctest pbctest2","time":"2017-08-16T08:21:11Z","shared":"yes","toread":"yes","href":"http://garfield.com","description":"description","extended":"extended","hash":"hash","meta":"meta"},
            {"tags":"pbctest pbctest2","time":"2017-08-16T08:21:11Z","shared":"yes","toread":"yes","href":"http://garfield.com","description":"description","extended":"extended","hash":"hash","meta":"meta"},
            {"tags":"pbctest pbctest2","time":"2017-08-16T08:21:11Z","shared":"yes","toread":"yes","href":"http://garfield.com","description":"description","extended":"extended","hash":"hash","meta":"meta"},
            {"tags":"pbctest pbctest2","time":"2017-08-16T08:21:11Z","shared":"yes","toread":"yes","href":"http://garfield.com","description":"description","extended":"extended","hash":"hash","meta":"meta"}
        ]

         """
		mockReply("/posts/all?meta=0&format=json&start=0&tag=twis&auth_token=$auth&results=10", json)
		val maxResults = 10
		val postsByTag = restTemplatePinboardClient.getAllPosts(arrayOf("twis"), 0, maxResults)
		assert(postsByTag.size == maxResults, { "there should be no more than 10 getAllPosts." })
	}

	@Test
	fun notes() {

		val noteDate = FormatUtils.encodeNoteDate(Date())
		val noteId = "123"
		val noteJson = """
            {
                "id": "$noteId" ,
                "title" : "title" ,
                "length" : 2,
                "created_at" : "$noteDate",
                "updated_at" : "$noteDate",
                "hash" : "1234"
            }
        """
		val notesJson = """
            {
                "count" : 3,
                "notes" : [ ${noteJson} ,${noteJson}, ${noteJson}]

            }
        """
		mockReply("/notes/list?format=json&auth_token=${auth}", notesJson)
		mockReply("/notes/${noteId}?format=json&auth_token=${auth}", noteJson)

		val userNotes = restTemplatePinboardClient.getUserNotes()
		assert(userNotes.count == userNotes.notes!!.size)
		val firstNote = userNotes.notes!![0]
		val firstId = firstNote.id

		val userNote = restTemplatePinboardClient.getUserNote(firstId!!)
		assert(userNote.id == firstId)
		assert(userNote.created == firstNote.created)
		assert(userNote.updated == firstNote.updated)
		assert(userNote.length == firstNote.length)
		assert(userNote.title == firstNote.title)
		mockRestServiceServer!!.verify()
	}

	@Test
	fun getUserTags() {
		val json = """ { "twis" : 2, "politics" : 4 } """
		mockReply("tags/get?${commonUriParams}", json)
		val tagCloud = restTemplatePinboardClient.getUserTags()
		val twisCount = tagCloud["twis"]!!
		assert(twisCount > 0)
		mockRestServiceServer!!.verify()
	}

	@Test
	fun suggestTagsForPost() {

		val json =
				"""
      [
          { "recommended" :[ "politics" , "trump" ] } ,
          { "popular" : []  }
      ]
    """

		val url = "http://infoq.com".trim()
		mockReply(""" posts/suggest?format=json&auth_token=${auth}&url=${url} """, json)

		val tagsForPost = restTemplatePinboardClient.suggestTagsForPost(url)
		assert(tagsForPost.recommended!!.isNotEmpty())

		mockRestServiceServer!!.verify()
	}

	private fun mockReply(uri: String,
	                      json: String,
	                      rc: ResponseCreator = withSuccess(json.trim(), MediaType.APPLICATION_JSON_UTF8),
	                      method: HttpMethod = HttpMethod.GET,
	                      count: ExpectedCount = manyTimes()) {
		val correctedUri: String by lazy {
			uri.trim().let { if (it.startsWith("/")) it.substring(1) else it }
		}
		this.mockRestServiceServer!!
				.expect(count, requestTo("https://api.pinboard.in/v1/${correctedUri}"))
				.andExpect(method(method))
				.andRespond(rc)
	}
}
