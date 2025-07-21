package ai.driftkit.clients.openai.utils;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.SneakyThrows;

import java.io.ByteArrayOutputStream;
import java.util.Base64;

public class OpenAIUtils {

    public static String imageToBase64(byte[] img) {
        String base64Url = Base64.getEncoder().encodeToString(img);
        return "data:image/jpeg;base64," + base64Url;
    }

    @SneakyThrows
    public static ImageData base64toBytes(String mimeType, String b64json) {
        String[] mime2base64 = b64json.split(",");

        String mime = mimeType;
        String base64Data = b64json;

        if (mime2base64.length == 2) {
            mime = mime2base64[0]
                    .replace("data:", "")
                    .replace(";base64,", "");

            base64Data = mime2base64[1];
        }

        byte[] decodedBytes = Base64.getDecoder().decode(base64Data);

        ByteArrayOutputStream bo = new ByteArrayOutputStream();
        bo.write(decodedBytes);

        return new ImageData(bo.toByteArray(), mime);
    }

    @Data
    @AllArgsConstructor
    public static class ImageData {
        private byte[] image;
        private String mimeType;
    }
}
