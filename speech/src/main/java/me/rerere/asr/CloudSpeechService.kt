package me.rerere.asr

import kotlinx.serialization.Serializable

/** Capabilities of these adapters, not every product sold by a vendor. */
@Serializable
enum class CloudSpeechService(val displayName: String, val endpoint: String, val model: String, val streaming: Boolean) {
    GROQ("Groq Whisper", "https://api.groq.com/openai/v1/audio/transcriptions", "whisper-large-v3-turbo", false),
    DEEPGRAM("Deepgram", "wss://api.deepgram.com/v1/listen", "nova-3", true),
    ASSEMBLYAI("AssemblyAI", "wss://streaming.assemblyai.com/v3/ws", "universal-3-6-pro", true),
    GOOGLE_CLOUD("Google Cloud Speech-to-Text", "https://speech.googleapis.com/v1/speech:recognize", "latest_short", false),
    AZURE("Azure Speech Services", "https://eastus.stt.speech.microsoft.com/speech/recognition/conversation/cognitiveservices/v1", "", false),
    ELEVENLABS("ElevenLabs Scribe", "wss://api.elevenlabs.io/v1/speech-to-text/realtime", "scribe_v2_realtime", true),
    GEMINI("Gemini", "https://generativelanguage.googleapis.com/v1beta", "gemini-2.5-flash", false),
}
