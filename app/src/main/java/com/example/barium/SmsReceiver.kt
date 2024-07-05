package com.example.barium

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.telephony.SmsManager
import android.telephony.SmsMessage
import android.util.Log

class SmsReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val bundle = intent.extras
        try {
            if (bundle != null) {
                val pdus = bundle["pdus"] as Array<*>
                for (pdu in pdus) {
                    val msg = SmsMessage.createFromPdu(pdu as ByteArray)
                    val msgBody = msg.messageBody
                    val sender = msg.originatingAddress

                    if (msgBody.startsWith("1234-Send-")) {
                        // Process the message here
                        Log.d("SmsReceiver", "Message received: $msgBody")

                        // Extract ID part and create acknowledgment message
                        val idPart = msgBody.substring(0, msgBody.indexOf(": ") + 2)
                        val acknowledgmentMessage = "1234-Ack-${msgBody.substring(10, msgBody.indexOf(": "))}: Acknowledgment received"
                        Log.d("SmsReceiver", "Acknowledgment message: $acknowledgmentMessage")

                        // Send acknowledgment
                        val smsManager = SmsManager.getDefault()
                        val messageParts = smsManager.divideMessage(acknowledgmentMessage)
                        smsManager.sendMultipartTextMessage(sender, null, messageParts, null, null)
                        Log.d("SmsReceiver", "Acknowledgment sent: $acknowledgmentMessage")
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("SmsReceiver", "Exception in onReceive: ${e.message}")
        }
    }
}
