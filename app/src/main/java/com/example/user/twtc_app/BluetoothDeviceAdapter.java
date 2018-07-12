package com.example.user.twtc_app;

import android.content.Context;
import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.RelativeLayout;
import android.widget.TextView;

import java.util.List;

public class BluetoothDeviceAdapter extends ArrayAdapter<Device> {

    int resource;
    String response;
    Context context;
    List<Device> itemList;

    public BluetoothDeviceAdapter(Context context, int resource, List<Device> items) {
        super(context, resource, items);
        this.resource=resource;
        this.itemList = items;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent)
    {
        RelativeLayout devicesView;
        Device device = getItem(position);
        if(convertView==null) {
            devicesView = new RelativeLayout(getContext());
            String inflater = Context.LAYOUT_INFLATER_SERVICE;
            LayoutInflater vi;
            vi = (LayoutInflater)getContext().getSystemService(inflater);
            vi.inflate(resource, devicesView, true);
        } else {
            devicesView = (RelativeLayout) convertView;
        }
        TextView deviceName =(TextView)devicesView.findViewById(R.id.text1);
        TextView deviceStatus = (TextView)devicesView.findViewById(R.id.text2);
        deviceName.setText(device.getName());
        deviceStatus.setText(device.getStatus());
        if(!isEnabled(position)){
            deviceName.setTextColor(Color.argb(80, 0, 0, 0));
            deviceStatus.setTextColor(Color.argb(80, 0, 0, 0));
        }
        return devicesView;
    }

    @Override
    public Device getItem(int position) {
        return itemList.get(position);
    }

    @Override
    public boolean isEnabled(int position) {
        if(getItem(position).getStatus().equals("已連線") || getItem(position).getStatus().equals("連線失敗")){
            return false;
        }
        return true;
    }
}

