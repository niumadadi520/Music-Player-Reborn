package com.mengsama.mod.mengsamanetmusic.api;

 
public final class NetEaseDirectQrProbe {
    public static void main(String[] args) throws Exception {
        var transport=new com.mengsama.mod.mengsamanetmusic.api.qq.QqHttp();
        var api=new NetEaseQrLogin(transport::exchange);
        String key=api.create();var status=api.check(key);
        System.out.println("{\"key_present\":"+(!key.isBlank())+",\"poll_code\":"+status.code()+",\"private_account_tested\":false}");
        if(status.code()!=801)throw new IllegalStateException("Unexpected anonymous QR status "+status.code());
    }
}
