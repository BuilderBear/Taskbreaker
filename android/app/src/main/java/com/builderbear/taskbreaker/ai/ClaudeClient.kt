package com.builderbear.taskbreaker.ai

import com.anthropic.client.okhttp.AnthropicOkHttpClient
import com.anthropic.errors.AnthropicIoException
import com.anthropic.errors.AnthropicServiceException
import com.anthropic.errors.RateLimitException
import com.anthropic.errors.UnauthorizedException
import com.anthropic.models.messages.MessageCreateParams
import com.anthropic.models.messages.OutputConfig
import com.anthropic.models.messages.StopReason
import com.builderbear.taskbreaker.breakdown.Step
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/** An error whose message is safe and helpful to show on screen as-is. */
class FriendlyError(message: String) : Exception(message)

/**
 * Asks Claude to split a task into small steps.
 *
 * Privacy: only the task text (and, for "Still too big", the parent task) is sent. Nothing about
 * medication or anything else in the app is sent.
 *
 * This makes a network call and waits for the answer, so it must run on a background thread
 * (the ViewModel does that). Running it on the main thread would freeze the screen.
 */
object ClaudeClient {
    // Haiku is Anthropic's fastest, cheapest model, plenty for writing a short list of steps.
    const val MODEL = "claude-haiku-5-5"

    val STUCK_LEVELS = listOf("A little", "Somewhat", "Pretty stuck", "Very stuck", "Totally frozen")

    fun breakDown(apiKey: String, task: String, level: Int, parent: String?): Result<List<Step>> {
        val client = AnthropicOkHttpClient.builder().apiKey(apiKey).build()
        return try {
            val params = MessageCreateParams.builder()
                .model(MODEL)
                .maxTokens(16000L)
                // Low effort: this is a short, simple job, so a quick answer is better than deep thinking.
                .outputConfig(OutputConfig.builder().effort(OutputConfig.Effort.LOW).build())
                .addUserMessage(buildPrompt(task, level, parent))
                .build()
            val response = client.messages().create(params)

            if (response.stopReason().orElse(null) == StopReason.REFUSAL) {
                return Result.failure(FriendlyError("Claude declined that one. Try rewording it, or add steps by hand."))
            }
            val text = response.content().mapNotNull { block -> block.text().orElse(null)?.text() }.joinToString("")
            Result.success(parseSteps(text))
        } catch (e: FriendlyError) {
            Result.failure(e)
        } catch (e: UnauthorizedException) {
            Result.failure(FriendlyError("Your API key was rejected. Check it in Settings."))
        } catch (e: RateLimitException) {
            Result.failure(FriendlyError("Too many requests just now. Wait a minute and try again."))
        } catch (e: AnthropicServiceException) {
            Result.failure(FriendlyError("Claude couldn't answer right now. Try again, or add steps by hand."))
        } catch (e: AnthropicIoException) {
            Result.failure(FriendlyError("Couldn't reach Claude. Check your internet connection."))
        } catch (e: JSONException) {
            Result.failure(FriendlyError("Got an unexpected reply. Try again, or add steps by hand."))
        } catch (e: Exception) {
            Result.failure(FriendlyError("That didn't work. Try again, or add steps by hand."))
        } finally {
            client.close()
        }
    }

    // Same instructions as the web version.
    private fun buildPrompt(task: String, level: Int, parent: String?): String {
        val n = listOf(3, 4, 5, 6, 8)[level - 1]
        val what = if (parent != null) {
            "They were working on \"$parent\" and this step still feels too big: \"$task\". Break just this step into smaller steps."
        } else {
            "Task: \"$task\"."
        }
        return """
            You help an adult with ADHD who is stuck and cannot get started. Stuck level: $level of 5 ("${STUCK_LEVELS[level - 1]}").
            $what
            Write about $n steps. Rules: the first step must be a tiny physical action that takes under 2 minutes and needs no decision (stand up, open the file, get the bag). Every step starts with a verb, is concrete and visible, under 12 words, no advice or pep talk. The more stuck they are, the smaller each step. Estimate whole minutes per step (1-30).
            Reply with only JSON: {"steps":[{"text":"...","min":2}]}
        """.trimIndent()
    }

    /** Pulls the JSON object out of Claude's reply and turns it into steps, ignoring anything malformed. */
    internal fun parseSteps(text: String): List<Step> {
        val start = text.indexOf('{')
        val end = text.lastIndexOf('}')
        if (start < 0 || end <= start) throw FriendlyError("No steps came back. Try again, or add steps by hand.")
        val array = JSONObject(text.substring(start, end + 1)).optJSONArray("steps") ?: JSONArray()
        val steps = (0 until array.length()).mapNotNull { i ->
            val obj = array.optJSONObject(i) ?: return@mapNotNull null
            val stepText = obj.optString("text").trim()
            if (stepText.isEmpty()) null else Step(stepText.take(140), obj.optInt("min", 5).coerceIn(1, 60))
        }
        if (steps.isEmpty()) throw FriendlyError("No steps came back. Try again, or add steps by hand.")
        return steps
    }
}
