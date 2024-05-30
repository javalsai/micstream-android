package com.micdroid.app

// NOTE: Code quality is 0 not to say negative
// TODO: handle socket close
// TODO: close connections
// TODO: manual rate
// TODO: custom output format
// TODO: custom mode (mono/stereo)
// TODO: inputs (maybe multiple mic sources)
// TODO: calculate buffer delay and buffer/rate network throughout live
// TODO: error handling
// NOTE: muting doesn't send zeroes, it just "pauses" the stream for that time

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Color
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.net.Socket


// TODO: send header on the stream with the config to play, prepared for the desktop part, make a raw box to optionally skip it
// NOTE: for now, the way to play this would be `ncat -nlvp <port> | pw-play --channels 1 --format s16 --rate 40800 -` (if you use pw)
class MainActivity : AppCompatActivity() {
    private val rate = 40800
    private val channelConfig = AudioFormat.CHANNEL_IN_MONO
    private val audioFormat = AudioFormat.ENCODING_PCM_16BIT

    private var sockets: MutableList<Socket> = ArrayList()
    private lateinit var recorder: AudioRecord
    private var mute = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val btnMute = findViewById<ImageButton>(R.id.mute)
        btnMute.setOnClickListener {
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.RECORD_AUDIO
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO), 0)
            } else {
                onMicPerms()
            }
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        onMicPerms()
    }

    private fun onMicPerms() {
        val hostsList = findViewById<RecyclerView>(R.id.hosts_list)
        val adapter = MyAdapter(mutableListOf())
        hostsList.layoutManager = LinearLayoutManager(this)
        hostsList.adapter = adapter

        val btnMute = findViewById<ImageButton>(R.id.mute)
        val btnConn = findViewById<Button>(R.id.button)
        val host = findViewById<EditText>(R.id.host)

        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.RECORD_AUDIO
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            Toast.makeText(this@MainActivity, "Record permissions are denied", Toast.LENGTH_SHORT).show()
            return
        }

        val bufCont = findViewById<LinearLayout>(R.id.bufCont)
        val bufSize = try {
            findViewById<EditText>(R.id.bufSize).text.toString().toInt()
        } catch (err: Exception) {
            Toast.makeText(
                this@MainActivity,
                "Couldn't parse bufSize",
                Toast.LENGTH_SHORT
            ).show()
            return
        }
        val bufUnit = findViewById<Spinner>(R.id.bufUnit).getSelectedItem().toString()

        val byteSize = bufSize * getBytesMulti(bufUnit)
        val minBufferSize = AudioRecord.getMinBufferSize(rate, channelConfig, audioFormat)
        if(byteSize < minBufferSize) {
            Toast.makeText(
                this@MainActivity,
                "Selected size is smaller than min ($byteSize < $minBufferSize)",
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        this.recorder = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            rate,
            channelConfig,
            audioFormat,
            byteSize
        )
        bufCont.visibility = View.INVISIBLE
        this.updateMuteColor()

        btnMute.setOnClickListener {
            this.mute = !this.mute
            this.updateMuteColor()
        }

        btnConn.setOnClickListener {
            try {
                Thread {
                    try {
                        sockets.add(parseHost(host.text.toString()))
                    } catch (err: Exception) {
                        runOnUiThread {
                            Toast.makeText(
                                this@MainActivity,
                                "E: $err",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                        return@Thread
                    }
                    runOnUiThread {
                        adapter.addItem(host.text.toString())
                    }
                }.start()
            } catch(e: Exception) {
                Toast.makeText(
                    this@MainActivity,
                    "T: $e",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }

        val buffer = ByteArray(byteSize)
        this.recorder.startRecording()
        Thread {
            while (true) {
                val read = this.recorder.read(buffer, 0, byteSize)
                if(read > 0 && !this.mute) {
                    val bufCopy = buffer.copyOf(read)
                    Thread {
                        for (socket in this.sockets) {
                            val out = socket.getOutputStream()
                            out.write(bufCopy)
                        }
                    }.start()
                }
            }
        }.start()
    }

    private fun updateMuteColor() {
        val btnMute = findViewById<ImageButton>(R.id.mute)
        val color = when(this.mute) {
            true -> Color.parseColor("#ff0000")
            false -> Color.parseColor("#6ab04c")
        }
        btnMute.setBackgroundColor(color)
    }

    private fun parseHost(host: String): Socket {
        val split = host.split(":")
        val port = split[1].toInt()

        val socket = Socket(split[0], port)
        return socket
    }

    private fun getBytesMulti(unit: String): Int {
        return when (unit) {
            "B" -> 1
            "K" -> 1000
            "Ki" -> 1024
            "M" -> 1000_000
            "Mi" -> 1048_576
            else -> 0
        }
    }
}

class MyAdapter(private val items: MutableList<String>) : RecyclerView.Adapter<MyAdapter.MyViewHolder>() {

    class MyViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val textView: TextView = itemView.findViewById(android.R.id.text1)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MyViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(android.R.layout.simple_list_item_1, parent, false)
        return MyViewHolder(view)
    }

    override fun onBindViewHolder(holder: MyViewHolder, position: Int) {
        holder.textView.text = items[position]
    }

    override fun getItemCount(): Int {
        return items.size
    }

    fun addItem(item: String) {
        items.add(item)
        notifyItemInserted(items.size - 1)
    }
}