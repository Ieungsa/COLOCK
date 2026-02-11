package com.ieungsa.myapplication

import android.content.Context
import android.util.Log
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.*

class DistilKoBertTokenizer(private val context: Context, private val vocabFileName: String) {

    private val TAG = "DistilKoBertTokenizer"
    private val vocab: MutableMap<String, Int> = mutableMapOf()
    private val reverseVocab: MutableMap<Int, String> = mutableMapOf()

    private val CLS_TOKEN = "[CLS]"
    private val SEP_TOKEN = "[SEP]"
    private val PAD_TOKEN = "[PAD]"
    private val UNK_TOKEN = "[UNK]"

    private val CLS_ID: Int
    private val SEP_ID: Int
    private val PAD_ID: Int
    private val UNK_ID: Int

    init {
        loadVocab()
        CLS_ID = vocab[CLS_TOKEN] ?: throw IllegalStateException("CLS token not found in vocab")
        SEP_ID = vocab[SEP_TOKEN] ?: throw IllegalStateException("SEP token not found in vocab")
        PAD_ID = vocab[PAD_TOKEN] ?: throw IllegalStateException("PAD token not found in vocab")
        UNK_ID = vocab[UNK_TOKEN] ?: throw IllegalStateException("UNK token not found in vocab")
    }

    private fun loadVocab() {
        try {
            val inputStream = context.assets.open(vocabFileName)
            val reader = BufferedReader(InputStreamReader(inputStream))
            var line: String?
            var index = 0
            while (reader.readLine().also { line = it } != null) {
                vocab[line!!] = index
                reverseVocab[index] = line!!
                index++
            }
            reader.close()
            inputStream.close()
            Log.d(TAG, "Vocabulary loaded successfully. Size: ${vocab.size}")
        } catch (e: Exception) {
            Log.e(TAG, "Error loading vocabulary from $vocabFileName", e)
            throw e
        }
    }

    fun tokenize(text: String, maxLength: Int): TokenizedInput {
        val tokens = mutableListOf<String>()
        // Simple whitespace tokenization for now, more advanced tokenization might be needed for KoBERT
        // For KoBERT, typically a SentencePiece tokenizer is used, but for simplicity and given the vocab.txt,
        // we'll assume word-level tokenization based on the vocab.
        // A more robust solution would involve integrating a proper KoBERT tokenizer.
        // For now, we'll just add UNK for now.
        // A real KoBERT tokenizer would break down unknown words into subwords.

        // Add CLS token
        tokens.add(CLS_TOKEN)

        // Tokenize input text
        text.split(" ").forEach { word ->
            if (vocab.containsKey(word)) {
                tokens.add(word)
            } else {
                // Handle subword tokenization or fall back to UNK
                // For simplicity, we'll just add UNK for now.
                // A real KoBERT tokenizer would break down unknown words into subwords.
                tokens.add(UNK_TOKEN)
            }
        }

        // Add SEP token
        tokens.add(SEP_TOKEN)

        // Convert tokens to IDs
        val inputIds = tokens.map { vocab[it] ?: UNK_ID }.toIntArray()

        // Truncate if longer than maxLength
        val truncatedInputIds = if (inputIds.size > maxLength) {
            inputIds.sliceArray(0 until maxLength)
        } else {
            inputIds
        }

        // Create attention mask and pad
        val attentionMask = IntArray(maxLength) { 0 }
        val paddedInputIds = IntArray(maxLength) { PAD_ID }

        for (i in truncatedInputIds.indices) {
            paddedInputIds[i] = truncatedInputIds[i]
            attentionMask[i] = 1
        }

        return TokenizedInput(paddedInputIds, attentionMask)
    }

    data class TokenizedInput(
        val inputIds: IntArray,
        val attentionMask: IntArray
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as TokenizedInput

            if (!inputIds.contentEquals(other.inputIds)) return false
            if (!attentionMask.contentEquals(other.attentionMask)) return false

            return true
        }

        override fun hashCode(): Int {
            var result = inputIds.contentHashCode()
            result = 31 * result + attentionMask.contentHashCode()
            return result
        }
    }
}
