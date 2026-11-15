package com.alltestsareus.app.data.remote

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class WebCourseImporterTest {

    private val importer = WebCourseImporter()

    @Test
    fun `extracts embedded course JSON from HTML page`() = runBlocking {
        val html = """
            <!DOCTYPE html>
            <html>
            <body>
              <h1>Test Page</h1>
              <script id="alltests-course-data" type="application/json">
              {
                "courseId": "html_embedded_1",
                "category": "orienteering",
                "version": 2,
                "title": "Embedded Orienteering Test",
                "lessons": []
              }
              </script>
            </body>
            </html>
        """.trimIndent()

        val extracted = importer.extractEmbeddedJsonFromHtml(html)
        assertNotNull(extracted)

        val courses = importer.parseWebPayload(html, "https://example.com/test.html")
        assertEquals(1, courses.size)
        assertEquals("html_embedded_1", courses[0].courseId)
        assertEquals("https://example.com/test.html", courses[0].sourceWebUrl)
    }

    @Test
    fun `parses TRUE_FALSE and MULTI_CHOICE questions from course JSON`() = runBlocking {
        val json = """
            {
              "courseId": "greek_a_20",
              "category": "languages",
              "version": 2,
              "title": "Greek Verbs A",
              "lessons": [
                {
                  "lessonId": "l1",
                  "title": "Lesson 1",
                  "concepts": [
                    {
                      "conceptId": "c1",
                      "term": "γράφω",
                      "questions": [
                        {
                          "questionId": "q_tf",
                          "type": "TRUE_FALSE",
                          "prompt": "γράφω means write",
                          "correctBool": true
                        },
                        {
                          "questionId": "q_mc",
                          "type": "MULTI_CHOICE",
                          "prompt": "Pick valid forms",
                          "options": [
                            { "id": "a", "text": "γράφεις", "isCorrect": true },
                            { "id": "b", "text": "γράφει", "isCorrect": true },
                            { "id": "c", "text": "γράφουνε_err", "isCorrect": false }
                          ]
                        }
                      ]
                    }
                  ]
                }
              ]
            }
        """.trimIndent()

        val courses = importer.parseWebPayload(json, "https://example.com/greek.json")
        assertEquals(1, courses.size)
        val questions = courses[0].lessons[0].concepts[0].questions
        assertEquals("TRUE_FALSE", questions[0].type)
        assertEquals(true, questions[0].correctBool)
        assertEquals("MULTI_CHOICE", questions[1].type)
        assertEquals(2, questions[1].options.count { it.isCorrect })
    }

    @Test
    fun parseWebPayloadResolvesRelativeGreekAudioUrls() {
        val json = """
            {
              "courseId": "greek_audio_test",
              "category": "languages",
              "version": 3,
              "title": "Greek Audio Test",
              "description": "Test",
              "speechLang": "el-GR",
              "lessons": [
                {
                  "lessonId": "l1",
                  "title": "L1",
                  "concepts": [
                    {
                      "conceptId": "c1",
                      "term": "κάνω",
                      "definition": "делать",
                      "audioText": "κάνω",
                      "audioUrl": "../audio/greek/01_kano.mp3",
                      "questions": [
                        {
                          "questionId": "q1",
                          "type": "WORD_ORDER",
                          "prompt": "Соберите фразу",
                          "audioText": "Θέλω να μαθαίνω ελληνικά",
                          "audioUrl": "../audio/greek/phrase_21.mp3",
                          "wordTokens": ["Θέλω", "να", "μαθαίνω", "ελληνικά"]
                        }
                      ]
                    }
                  ]
                }
              ]
            }
        """.trimIndent()

        val courses = importer.parseWebPayload(
            json,
            "http://192.168.0.225:8085/courses/languages-greek-verbs-a.json"
        )
        assertEquals(1, courses.size)
        assertEquals("el-GR", courses[0].speechLang)
        val concept = courses[0].lessons[0].concepts[0]
        assertEquals("κάνω", concept.audioText)
        assertEquals(
            "http://192.168.0.225:8085/audio/greek/01_kano.mp3",
            concept.audioUrl
        )
        assertEquals(
            "http://192.168.0.225:8085/audio/greek/phrase_21.mp3",
            concept.questions[0].audioUrl
        )
    }
}
