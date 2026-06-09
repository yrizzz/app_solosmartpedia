package com.solosmartpedia.app

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetDialogFragment

class PrinterBottomSheet(
    private val printerManager: BluetoothPrinterManager,
    private val onStatusChanged: (connected: Boolean, name: String) -> Unit
) : BottomSheetDialogFragment() {

    @SuppressLint("MissingPermission")
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val root = inflater.inflate(R.layout.bottom_sheet_printer, container, false)

        val rvDevices      = root.findViewById<RecyclerView>(R.id.rvDevices)
        val tvNoPaired     = root.findViewById<TextView>(R.id.tvNoPaired)
        val layoutConnected = root.findViewById<LinearLayout>(R.id.layoutConnected)
        val tvConnected    = root.findViewById<TextView>(R.id.tvConnectedName)
        val btnTest        = root.findViewById<View>(R.id.btnTestPrint)
        val btnDisconnect  = root.findViewById<View>(R.id.btnDisconnect)
        val btnRefresh     = root.findViewById<View>(R.id.btnRefresh)

        rvDevices.layoutManager = LinearLayoutManager(requireContext())

        fun refresh() {
            val devices = printerManager.getPairedDevices()

            if (printerManager.isConnected) {
                layoutConnected.visibility = View.VISIBLE
                tvConnected.text = "Terhubung: ${printerManager.connectedDeviceName}"
            } else {
                layoutConnected.visibility = View.GONE
            }

            if (devices.isEmpty()) {
                tvNoPaired.visibility = View.VISIBLE
                rvDevices.visibility = View.GONE
            } else {
                tvNoPaired.visibility = View.GONE
                rvDevices.visibility = View.VISIBLE
                rvDevices.adapter = BluetoothDeviceAdapter(
                    devices,
                    if (printerManager.isConnected) printerManager.connectedDeviceName else null
                ) { name, address ->
                    Toast.makeText(context, "Menghubungkan ke $name…", Toast.LENGTH_SHORT).show()
                    printerManager.connect(address,
                        onSuccess = { devName ->
                            Toast.makeText(context, "Terhubung: $devName", Toast.LENGTH_SHORT).show()
                            onStatusChanged(true, devName)
                            refresh()
                        },
                        onError = { err ->
                            Toast.makeText(context, "Gagal: $err", Toast.LENGTH_SHORT).show()
                        }
                    )
                }
            }
        }

        btnRefresh.setOnClickListener { refresh() }

        btnTest.setOnClickListener {
            printerManager.printTest(
                onSuccess = { Toast.makeText(context, "Tes print berhasil!", Toast.LENGTH_SHORT).show() },
                onError   = { err -> Toast.makeText(context, "Error: $err", Toast.LENGTH_SHORT).show() }
            )
        }

        btnDisconnect.setOnClickListener {
            printerManager.disconnect()
            onStatusChanged(false, "")
            refresh()
            Toast.makeText(context, "Printer diputus", Toast.LENGTH_SHORT).show()
        }

        refresh()
        return root
    }
}
