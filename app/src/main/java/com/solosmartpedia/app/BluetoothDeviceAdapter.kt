package com.solosmartpedia.app

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class BluetoothDeviceAdapter(
    private val devices: List<Pair<String, String>>,
    private val connectedAddress: String?,
    private val onClick: (name: String, address: String) -> Unit
) : RecyclerView.Adapter<BluetoothDeviceAdapter.VH>() {

    inner class VH(view: View) : RecyclerView.ViewHolder(view) {
        val name: TextView    = view.findViewById(R.id.tvDeviceName)
        val address: TextView = view.findViewById(R.id.tvDeviceAddress)
        val check: ImageView  = view.findViewById(R.id.ivConnected)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH =
        VH(LayoutInflater.from(parent.context).inflate(R.layout.item_bt_device, parent, false))

    override fun getItemCount() = devices.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        val (name, address) = devices[position]
        holder.name.text = name
        holder.address.text = address
        val isConnected = address == connectedAddress
        holder.check.visibility = if (isConnected) View.VISIBLE else View.GONE
        holder.itemView.setOnClickListener { onClick(name, address) }
    }
}
