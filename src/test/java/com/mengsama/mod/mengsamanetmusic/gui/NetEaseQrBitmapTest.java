package com.mengsama.mod.mengsamanetmusic.gui;
import com.google.zxing.*;
import com.google.zxing.common.HybridBinarizer;
import com.google.zxing.qrcode.*;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import com.mengsama.mod.mengsamanetmusic.api.NetEaseQrLogin;
import org.junit.jupiter.api.Test;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
class NetEaseQrBitmapTest {
    @Test void locallyRenderedModulesDecodeBackToOfficialLoginLink() throws Exception {
        String url=NetEaseQrLogin.loginUrl("test_key_1234567890");
        var matrix=new QRCodeWriter().encode(url,BarcodeFormat.QR_CODE,1,1,Map.of(EncodeHintType.MARGIN,4,EncodeHintType.ERROR_CORRECTION,ErrorCorrectionLevel.M));
        int size=matrix.getWidth()*3;int[] pixels=new int[size*size];
        for(int y=0;y<size;y++)for(int x=0;x<size;x++)pixels[y*size+x]=matrix.get(x/3,y/3)?0xFF000000:0xFFFFFFFF;
        var decoded=new QRCodeReader().decode(new BinaryBitmap(new HybridBinarizer(new RGBLuminanceSource(size,size,pixels))));
        assertEquals(url,decoded.getText());
        var bounds=QqLoginLayout.fitImage(QqLoginLayout.fit(427,240).qrImageArea(),matrix.getWidth(),matrix.getHeight());
        assertEquals(0,bounds.width()%matrix.getWidth());assertEquals(bounds.width(),bounds.height());
    }
}
