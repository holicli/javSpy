package org.holic.javspy.misc;
import okhttp3.*;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * 使用OkHttp的Server酱发送器
 */
public class ServerChanOkHttpSender {

    private static final OkHttpClient client = new OkHttpClient();

    /**
     * 发送Turbo版消息
     */
    public static String sendTurboMessage(String sendKey, String title, String desp) {
        try {
            String url = "https://sctapi.ftqq.com/" + sendKey + ".send";

            // 构建表单数据
            FormBody.Builder formBuilder = new FormBody.Builder(StandardCharsets.UTF_8);
            formBuilder.add("title", title);
            if (desp != null && !desp.isEmpty()) {
                formBuilder.add("desp", desp);
            }

            Request request = new Request.Builder()
                    .url(url)
                    .post(formBuilder.build())
                    .build();

            try (Response response = client.newCall(request).execute()) {
                if (response.isSuccessful() && response.body() != null) {
                    String responseBody = response.body().string();
                    return "发送成功: " + responseBody;
                } else {
                    return "发送失败，状态码: " + response.code();
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            return "发送异常: " + e.getMessage();
        }
    }

    /**
     * 发送消息并返回JSON解析结果
     */
    public static MessageResult sendMessageWithResult(String sendKey, String title, String desp) {
        MessageResult result = new MessageResult();

        try {
            String url = "https://sctapi.ftqq.com/" + sendKey + ".send";

            FormBody.Builder formBuilder = new FormBody.Builder(StandardCharsets.UTF_8);
            formBuilder.add("title", title);
            if (desp != null && !desp.isEmpty()) {
                formBuilder.add("desp", desp);
            }

            Request request = new Request.Builder()
                    .url(url)
                    .post(formBuilder.build())
                    .build();

            try (Response response = client.newCall(request).execute()) {
                result.setStatusCode(response.code());
                result.setSuccess(response.isSuccessful());

                if (response.body() != null) {
                    String responseBody = response.body().string();
                    result.setResponseBody(responseBody);

                    // 简单解析（实际项目中可以用JSON库如Gson/Jackson解析）
                    if (responseBody.contains("\"code\":0")) {
                        result.setMessage("发送成功");
                    } else if (responseBody.contains("\"code\":40001")) {
                        result.setMessage("SendKey错误");
                    } else {
                        result.setMessage("发送失败");
                    }
                }
            }
        } catch (Exception e) {
            result.setSuccess(false);
            result.setMessage("发送异常: " + e.getMessage());
        }

        return result;
    }

    /**
     * 消息发送结果封装类
     */
    public static class MessageResult {
        private boolean success;
        private String message;
        private int statusCode;
        private String responseBody;

        // getter和setter方法
        public boolean isSuccess() { return success; }
        public void setSuccess(boolean success) { this.success = success; }

        public String getMessage() { return message; }
        public void setMessage(String message) { this.message = message; }

        public int getStatusCode() { return statusCode; }
        public void setStatusCode(int statusCode) { this.statusCode = statusCode; }

        public String getResponseBody() { return responseBody; }
        public void setResponseBody(String responseBody) { this.responseBody = responseBody; }

        @Override
        public String toString() {
            return String.format("MessageResult{success=%s, message='%s', statusCode=%d}",
                    success, message, statusCode);
        }
    }

    /**
     * 使用示例
     */
    public static void main(String[] args) {
        String sendKey = "SCT143060TvdXG7wJJmOtFAD6ZcVlL5WBY";

        // 发送消息
        String result = sendTurboMessage(sendKey, "测试消息", "这是一条测试消息");
        System.out.println("发送结果: " + result);

        // 发送并获取详细结果
        MessageResult detailedResult = sendMessageWithResult(sendKey, "详细报告", "测试消息内容");
        System.out.println("详细结果: " + detailedResult);
    }
}