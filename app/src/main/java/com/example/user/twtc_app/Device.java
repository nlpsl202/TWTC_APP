package com.example.user.twtc_app;

public class Device {
    private String name;
    private String status;

    public Device(String name, String status) {
        this.name = name;
        this.status = status;
    }

    public String getName(){
        return name;
    }

    public void setName(String name){
        this.name = name;
    }

    public String getStatus(){
        return status;
    }

    public void setStatus(String status){
        this.status = status;
    }
}
