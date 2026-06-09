package com.solosmartpedia.app

import android.nfc.Tag
import android.nfc.tech.*

object NfcHelper {

    fun getCardUid(tag: Tag): String {
        return tag.id.toHexString()
    }

    fun getCardType(tag: Tag): String {
        val techs = tag.techList
        return when {
            techs.contains(MifareClassic::class.java.name)    -> detectMifareClassic(tag)
            techs.contains(IsoDep::class.java.name)           -> detectIsoDep(tag)
            techs.contains(MifareUltralight::class.java.name) -> "Mifare Ultralight"
            techs.contains(NfcF::class.java.name)             -> "FeliCa (NFC-F)"
            techs.contains(NfcB::class.java.name)             -> "NFC-B"
            techs.contains(NfcV::class.java.name)             -> "NFC-V"
            techs.contains(NfcA::class.java.name)             -> "NFC-A"
            else                                               -> "Unknown"
        }
    }

    // Detect common Indonesian e-money cards via AID
    private fun detectIsoDep(tag: Tag): String {
        return try {
            val isoDep = IsoDep.get(tag)
            isoDep.connect()
            // Select PPSE (Payment System Environment)
            val selectPPSE = byteArrayOf(
                0x00, 0xA4.toByte(), 0x04, 0x00, 0x0E,
                0x32, 0x50, 0x41, 0x59, 0x2E, 0x53, 0x59,
                0x53, 0x2E, 0x44, 0x44, 0x46, 0x30, 0x31, 0x00
            )
            val response = isoDep.transceive(selectPPSE)
            isoDep.close()

            when {
                response.contains("MANDIRI".toByteArray()) ||
                response.toHexString().contains("A0000001415454") -> "e-Money Mandiri"
                response.contains("FLAZZ".toByteArray())          -> "Flazz BCA"
                response.toHexString().contains("A0000003974600") -> "TapCash BNI"
                response.toHexString().contains("A0000005240010") -> "Brizzi BRI"
                else                                               -> "Kartu NFC (ISO-DEP)"
            }
        } catch (e: Exception) {
            "Kartu NFC (ISO-DEP)"
        }
    }

    private fun detectMifareClassic(tag: Tag): String {
        return try {
            val mifare = MifareClassic.get(tag)
            when (mifare.type) {
                MifareClassic.TYPE_CLASSIC -> "Mifare Classic"
                MifareClassic.TYPE_PLUS    -> "Mifare Plus"
                MifareClassic.TYPE_PRO     -> "Mifare Pro"
                else                       -> "Mifare Classic"
            }
        } catch (e: Exception) {
            "Mifare Classic"
        }
    }

    private fun ByteArray.toHexString(): String =
        joinToString("") { "%02X".format(it) }

    private fun ByteArray.contains(other: ByteArray): Boolean {
        if (other.isEmpty()) return true
        outer@ for (i in 0..size - other.size) {
            for (j in other.indices) {
                if (this[i + j] != other[j]) continue@outer
            }
            return true
        }
        return false
    }
}

fun ByteArray.toHexString(): String = joinToString("") { "%02X".format(it) }
