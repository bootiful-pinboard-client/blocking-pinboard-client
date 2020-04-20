package pinboard.resttemplate

import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.test.autoconfigure.json.AutoConfigureJsonTesters
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Bean
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
import pinboard.PinboardClient
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
		classes = [MockPinboardClientTest.Config::class],
		properties = ["pinboard.token=1234"]
)
@AutoConfigureJsonTesters
class MockPinboardClientTest(
		@Autowired val restTemplate: RestTemplate,
		@Autowired val pinboardClient: PinboardClient) {

	@SpringBootApplication
	class Config {

		@Bean
		fun restTemplate(): RestTemplate = RestTemplate()
	}

	private var mockRestServiceServer: MockRestServiceServer? = null
	private val auth = "1234"
	private val commonUriParams = """ auth_token=${auth}&format=json    """.trim()
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
		mockReply("/posts/all?auth_token=$auth&format=json&fromdt=${FormatUtils.encodeDate(fromdt)}&meta=0&results=-1&start=0&tag=twis", json)
		val postsByDate = pinboardClient.getAllPosts(arrayOf("twis"), fromdt = fromdt)
		val comparator = Comparator<Bookmark> { a, b -> a.time!!.compareTo(b.time) }
		val minBookmark = postsByDate.minWith(comparator)
		assert(minBookmark!!.time!!.after(fromdt))
	}


	@Test
	fun addPost() {

		val encodeDate = FormatUtils.encodeDate(bookmark.time!!)
		mockReply("""
            /posts/add?auth_token=$auth&description=description&dt=$encodeDate&extended=extended&format=json&replace=yes&shared=yes&tags=pbctest%20pbctest2&toread=yes&url=http://garfield.com"""
				, """  { "status" : "done"}  """)
		val post = pinboardClient.addPost(bookmark.href!!, bookmark.description!!, bookmark.extended!!, bookmark.tags, bookmark.time!!, true, true, true)
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
		mockReply("/posts/get?auth_token=$auth&format=json&url=http://garfield.com", json)

		val result = pinboardClient.getPosts(bookmark.href)
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
		val uri = """ /posts/recent?auth_token=$auth&count=15&format=json&tag=pbctest%20pbctest2  """
		mockReply(uri, json)
		val result = pinboardClient.getRecentPosts(tag = bookmark.tags)
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
		mockReply("/posts/all?auth_token=$auth&format=json&meta=0&results=-1&start=0&tag=pbctest%20pbctest2", json)
		assert(bookmark.href != null)
		assert(this.pinboardClient.getAllPosts(bookmark.tags).size == 1)
	}

	@Test
	fun getNoOfPostsByDate() {

		val json = """{"tag":"twis cats","user":"starbuxman","dates":{"2017-08-16T08:31:25.755+0000":5}} """
		mockReply("/posts/dates?auth_token=$auth&format=json&tag=twis", json)

		val tagName = "twis"
		val result = pinboardClient.getCountOfPostsByDate(arrayOf(tagName))
		assert(result.user!!.toLowerCase() == "starbuxman")
		assert(result.dates!!.isNotEmpty())
		assert(result.tag!!.contains(tagName))

		mockRestServiceServer!!.verify()
	}

	@Test
	fun deleteTag() {
		val first = this.pinboardClientTestTag.first()
		val uri = """
         tags/delete?auth_token=${auth}&format=json&tag=${first}
        """
		mockReply(uri, """ { "status" : "done" } """)
		assert(pinboardClient.deleteTag(first))
	}

	@Test
	fun secret() {
		mockReply("/user/secret?auth_token=${auth}&format=json", """  {  "result" : "1234" } """)
		val userSecret = pinboardClient.getUserSecret()
		assert(userSecret.isNotBlank(), { "the userSecret should not be null" })
	}

	@Test
	fun apiToken() {
		mockReply("/user/api_token/?auth_token=${auth}&format=json", """ { "result" : "${auth}" }  """)
		val token = pinboardClient.getApiToken()
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
		val uri = """  /posts/all?auth_token=${ auth }&format=json&meta=0&results=-1&start=0&tag=${twisTag}  """.trimIndent()
		mockReply(uri, json)

		val postsByTag = pinboardClient.getAllPosts(arrayOf(twisTag))
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
		mockReply("/posts/all?auth_token=$auth&format=json&meta=0&results=10&start=0&tag=twis", json)
		val maxResults = 10
		val postsByTag = pinboardClient.getAllPosts(arrayOf("twis"), 0, maxResults)
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
		mockReply("/notes/list?auth_token=$auth&format=json", notesJson)
		mockReply("/notes/${noteId}?auth_token=$auth&format=json", noteJson)

		val userNotes = pinboardClient.getUserNotes()
		assert(userNotes.count == userNotes.notes!!.size)
		val firstNote = userNotes.notes!![0]
		val firstId = firstNote.id

		val userNote = pinboardClient.getUserNote(firstId!!)
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
		val tagCloud = pinboardClient.getUserTags()
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
		mockReply(""" posts/suggest?auth_token=${auth}&format=json&url=${url} """, json)

		val tagsForPost = pinboardClient.suggestTagsForPost(url)
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
