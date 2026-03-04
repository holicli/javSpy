package org.holic.javspy.misc;
import org.apache.http.HttpEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import java.nio.charset.StandardCharsets;
import java.net.URLEncoder;

/**
 * Server酱消息推送工具类
 * 支持Turbo版（默认）和普通版
 */
public class ServerChanSender {

    /**
     * 发送消息（Turbo版 - 默认）
     * @param sendKey Turbo版的SendKey
     * @param title 消息标题
     * @param desp 消息内容（支持Markdown）
     * @return 发送结果
     */
    public static String sendMessage(String sendKey, String title, String desp) {
        return sendTurboMessage(sendKey, title, desp);
    }

    /**
     * 发送Turbo版消息
     * @param sendKey Turbo版的SendKey
     * @param title 消息标题
     * @param desp 消息内容（支持Markdown）
     * @return 发送结果
     */
    public static String sendTurboMessage(String sendKey, String title, String desp) {
        try (CloseableHttpClient httpClient = HttpClients.createDefault()) {
            String url = "https://sctapi.ftqq.com/" + sendKey + ".send";

            HttpPost httpPost = new HttpPost(url);
            httpPost.setHeader("Content-Type", "application/x-www-form-urlencoded; charset=utf-8");

            // 构建表单数据
            StringBuilder formData = new StringBuilder();
            formData.append("title=").append(URLEncoder.encode(title, "UTF-8"));
            if (desp != null && !desp.isEmpty()) {
                formData.append("&desp=").append(URLEncoder.encode(desp, "UTF-8"));
            }

            // 设置请求体
            StringEntity entity = new StringEntity(formData.toString(), StandardCharsets.UTF_8);
            httpPost.setEntity(entity);

            // 执行请求
            try (CloseableHttpResponse response = httpClient.execute(httpPost)) {
                HttpEntity responseEntity = response.getEntity();
                String result = EntityUtils.toString(responseEntity, StandardCharsets.UTF_8);

                // 解析响应
                if (response.getStatusLine().getStatusCode() == 200) {
                    return "发送成功: " + result;
                } else {
                    return "发送失败，状态码: " + response.getStatusLine().getStatusCode() + ", 响应: " + result;
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            return "发送异常: " + e.getMessage();
        }
    }

    /**
     * 发送普通版消息（兼容旧版）
     * @param sendKey 普通版的SCKEY
     * @param title 消息标题
     * @param desp 消息内容（可选）
     * @return 发送结果
     */
    public static String sendLegacyMessage(String sendKey, String title, String desp) {
        try (CloseableHttpClient httpClient = HttpClients.createDefault()) {
            String url = "https://sc.ftqq.com/" + sendKey + ".send";

            HttpPost httpPost = new HttpPost(url);
            httpPost.setHeader("Content-Type", "application/x-www-form-urlencoded; charset=utf-8");

            // 构建表单数据
            StringBuilder formData = new StringBuilder();
            formData.append("text=").append(URLEncoder.encode(title, "UTF-8"));
            if (desp != null && !desp.isEmpty()) {
                formData.append("&desp=").append(URLEncoder.encode(desp, "UTF-8"));
            }

            // 设置请求体
            StringEntity entity = new StringEntity(formData.toString(), StandardCharsets.UTF_8);
            httpPost.setEntity(entity);

            // 执行请求
            try (CloseableHttpResponse response = httpClient.execute(httpPost)) {
                HttpEntity responseEntity = response.getEntity();
                String result = EntityUtils.toString(responseEntity, StandardCharsets.UTF_8);

                if (response.getStatusLine().getStatusCode() == 200) {
                    return "发送成功: " + result;
                } else {
                    return "发送失败，状态码: " + response.getStatusLine().getStatusCode();
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            return "发送异常: " + e.getMessage();
        }
    }

    /**
     * 发送HTML格式的消息（仅Turbo版支持）
     * @param sendKey Turbo版的SendKey
     * @param title 消息标题
     * @param htmlContent 消息内容（HTML格式）
     * @return 发送结果
     */
    public static String sendHtmlMessage(String sendKey, String title, String htmlContent) {
        try (CloseableHttpClient httpClient = HttpClients.createDefault()) {
            String url = "https://sctapi.ftqq.com/" + sendKey + ".send";

            HttpPost httpPost = new HttpPost(url);
            httpPost.setHeader("Content-Type", "application/x-www-form-urlencoded; charset=utf-8");

            // 构建表单数据
            StringBuilder formData = new StringBuilder();
            formData.append("title=").append(URLEncoder.encode(title, "UTF-8"));
            formData.append("&desp=").append(URLEncoder.encode(htmlContent, "UTF-8"));

            StringEntity entity = new StringEntity(formData.toString(), StandardCharsets.UTF_8);
            httpPost.setEntity(entity);

            try (CloseableHttpResponse response = httpClient.execute(httpPost)) {
                HttpEntity responseEntity = response.getEntity();
                String result = EntityUtils.toString(responseEntity, StandardCharsets.UTF_8);
                return "发送结果: " + result;
            }
        } catch (Exception e) {
            e.printStackTrace();
            return "发送异常: " + e.getMessage();
        }
    }

    /**
     * 发送带频道的消息（Turbo版特有功能）
     * @param sendKey Turbo版的SendKey
     * @param channel 频道编号，如：9（方糖服务号）、66（测试频道）
     * @param title 消息标题
     * @param desp 消息内容
     * @return 发送结果
     */
    public static String sendMessageWithChannel(String sendKey, int channel, String title, String desp) {
        try (CloseableHttpClient httpClient = HttpClients.createDefault()) {
            String url = "https://sctapi.ftqq.com/" + sendKey + ".send";

            HttpPost httpPost = new HttpPost(url);
            httpPost.setHeader("Content-Type", "application/x-www-form-urlencoded; charset=utf-8");

            // 构建表单数据
            StringBuilder formData = new StringBuilder();
            formData.append("title=").append(URLEncoder.encode(title, "UTF-8"));
            formData.append("&desp=").append(URLEncoder.encode(desp, "UTF-8"));
            formData.append("&channel=").append(channel);

            StringEntity entity = new StringEntity(formData.toString(), StandardCharsets.UTF_8);
            httpPost.setEntity(entity);

            try (CloseableHttpResponse response = httpClient.execute(httpPost)) {
                HttpEntity responseEntity = response.getEntity();
                String result = EntityUtils.toString(responseEntity, StandardCharsets.UTF_8);
                return "发送结果: " + result;
            }
        } catch (Exception e) {
            e.printStackTrace();
            return "发送异常: " + e.getMessage();
        }
    }

    /**
     * 使用示例
     */
    public static void main(String[] args) {
        // 你的Server酱SendKey（Turbo版）
        String turboSendKey = "SCT143060TvdXG7wJJmOtFAD6ZcVlL5WBY";

        // 你的Server酱SCKEY（普通版，如果还在使用）
        String legacySckey = "SCUXXXXXXXXXXXXX";

        // 示例1：发送简单的文本消息
        System.out.println("=== 发送简单消息 ===");
        String result1 = sendTurboMessage(turboSendKey, "服务器状态通知", "服务器运行正常，一切OK！");
        System.out.println(result1);

        // 示例2：发送Markdown格式消息
        System.out.println("\n=== 发送Markdown消息 ===");
        String markdownContent = "## 系统监控报告\n" +
                "**时间**: " + new java.util.Date() + "\n" +
                "**状态**: ✅ 正常\n" +
                "**CPU使用率**: 45%\n" +
                "**内存使用率**: 68%\n" +
                "**磁盘空间**: 120GB / 500GB\n" +
                "```\n" +
                "最近操作日志：\n" +
                "1. 备份完成\n" +
                "2. 安全扫描通过\n" +
                "3. 用户登录正常\n" +
                "```";

        String result2 = sendTurboMessage(turboSendKey, "📊 系统监控报告", markdownContent);
        System.out.println(result2);

        // 示例3：发送到特定频道
        System.out.println("\n=== 发送到特定频道 ===");
        String result3 = sendMessageWithChannel(turboSendKey, 9, "重要通知", "请及时处理待办事项");
        System.out.println(result3);

        // 示例4：发送普通版消息（兼容旧版）
        System.out.println("\n=== 发送普通版消息 ===");
        String result4 = sendLegacyMessage(legacySckey, "测试消息", "这是普通版Server酱消息");
        System.out.println(result4);
    }
}