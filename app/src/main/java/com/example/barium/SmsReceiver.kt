package com.example.barium

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.telephony.SmsManager
import android.telephony.SmsMessage
import android.util.Log
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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
                    val nowTime = SimpleDateFormat("yyyyMMddHHmmss", Locale.getDefault()).format(
                        Date()
                    )

                    if (msgBody.startsWith("1234-Send-")) {
                        // Process the message here
                        Log.d("SmsReceiver", "Message received: $msgBody")

                        // Extract ID part and create acknowledgment message
                        val idPart = msgBody.substring(0, msgBody.indexOf(": ") + 2)
                        val acknowledgmentMessage = "1234-Ack-${msgBody.substring(10, msgBody.indexOf(": "))}: Acknowledgment received"

                        // Send acknowledgment
                        val smsManager = SmsManager.getDefault()
                        val messageParts = smsManager.divideMessage(acknowledgmentMessage)
                        smsManager.sendMultipartTextMessage(sender, null, messageParts, null, null)
                        Log.d("SmsReceiver", "Acknowledgment sent: $acknowledgmentMessage")

                        // Add log for received message
                        val receivedIntent = Intent("com.example.barium.SMS_RECEIVED_LOG")
                        receivedIntent.putExtra("message", msgBody)
                        receivedIntent.putExtra("status", "received")
                        receivedIntent.putExtra("id", nowTime)
                        LocalBroadcastManager.getInstance(context).sendBroadcast(receivedIntent)

                    } else if (msgBody.startsWith("1234-Ack-")) {
                        // Process the acknowledgment message
                        Log.d("SmsReceiver", "Acknowledgment received: $msgBody")

                        // Extract the message ID from the acknowledgment
                        val messageId = msgBody.substring(0, msgBody.indexOf(":"))

                        // Notify the MainActivity of the acknowledgment
                        val acknowledgmentIntent = Intent("com.example.barium.SMS_ACKNOWLEDGMENT")
                        acknowledgmentIntent.putExtra("message_id", messageId)
                        LocalBroadcastManager.getInstance(context).sendBroadcast(acknowledgmentIntent)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("SmsReceiver", "Exception in onReceive: ${e.message}")
        }
    }
}
