package com.example.car

import android.bluetooth.BluetoothDevice
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class DeviceListAdapter(private val onClick: (BluetoothDevice) -> Unit) :
    RecyclerView.Adapter<DeviceListAdapter.DeviceViewHolder>() {

    private var devices: List<BluetoothDevice> = emptyList()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DeviceViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(android.R.layout.simple_list_item_1, parent, false)
        return DeviceViewHolder(view, onClick)
    }

    override fun onBindViewHolder(holder: DeviceViewHolder, position: Int) {
        holder.bind(devices[position])
    }

    override fun getItemCount(): Int = devices.size

    fun updateDevices(newDevices: List<BluetoothDevice>) {
        devices = newDevices
        notifyDataSetChanged()
    }

    class DeviceViewHolder(itemView: View, val onClick: (BluetoothDevice) -> Unit) :
        RecyclerView.ViewHolder(itemView) {
        private val textView: TextView = itemView.findViewById(android.R.id.text1)

        fun bind(device: BluetoothDevice) {
            textView.text = device.name ?: device.address
            itemView.setOnClickListener { onClick(device) }
        }
    }
}
