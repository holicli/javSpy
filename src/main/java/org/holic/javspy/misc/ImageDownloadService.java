package org.holic.javspy.misc;

import org.apache.http.HttpEntity;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.springframework.stereotype.Service;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.net.URLDecoder;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Service
public class ImageDownloadService {

    // 图片存储路径
    private static final String IMAGE_STORAGE_PATH = "../pic";
    private static final String IMAGE_STORAGE_PATH_FNOS = "/vol1/1000/java/pic";

    // 图片访问的基础URL
    private static final String BASE_URL = "http://localhost:8084";

    // 连接超时设置
    private static final int CONNECT_TIMEOUT = 10000;
    private static final int SOCKET_TIMEOUT = 30000;

    /**
     * 获取图片URL（检查存在性，不存在则下载）
     */
    public String getImageUrl(String imageUrl, String fileName) throws IOException {
        return getImageUrl(imageUrl, fileName, null);
    }

    /**
     * 获取图片URL（检查存在性，不存在则下载）；referer 非空时随请求发送。
     */
    public String getImageUrl(String imageUrl, String fileName, String referer) throws IOException {
        // 确保目录存在

        Path storageDir = Paths.get(getImgPath());
        if (!Files.exists(storageDir)) {
            Files.createDirectories(storageDir);
        }

        // 如果未提供文件名，从URL生成
        if (fileName == null || fileName.trim().isEmpty()) {
            fileName = generateFileName(imageUrl);
        }

        // 构建本地文件路径
        Path localFilePath = storageDir.resolve(fileName);

        // 检查文件是否已存在
        if (Files.exists(localFilePath)) {
            return generateAccessUrl(fileName);
        }

        // 下载图片
        boolean downloadSuccess = false;

        try {
            downloadSuccess = downloadImageWithApache(imageUrl, localFilePath, referer);
        } catch (Exception e) {
            // 捕获所有异常，包括超时
            System.err.println("下载图片失败（可能是超时）: " + imageUrl);
            e.printStackTrace();
            downloadSuccess = false;
            return null; // 或者返回一个默认图片地址
        }

        if (downloadSuccess) {
            return generateAccessUrl(fileName);
        } else {
            throw new IOException("图片下载失败: " + imageUrl);
        }
    }

    /**
     * 使用Apache HttpClient下载图片
     */
    private boolean downloadImageWithApache(String imageUrl, Path savePath, String referer) throws IOException {
        // 创建HttpClient
        try (CloseableHttpClient httpClient = HttpClients.createDefault()) {

            // 配置请求
            RequestConfig requestConfig = RequestConfig.custom()
                    .setConnectTimeout(CONNECT_TIMEOUT)
                    .setSocketTimeout(SOCKET_TIMEOUT)
                    .setConnectionRequestTimeout(CONNECT_TIMEOUT)
                    .build();

            HttpGet httpGet = new HttpGet(imageUrl);
            httpGet.setConfig(requestConfig);
            httpGet.setHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36");
            httpGet.setHeader("Accept", "image/webp,image/apng,image/*,*/*;q=0.8");
            if (referer != null && !referer.trim().isEmpty()) {
                httpGet.setHeader("Referer", referer);
            }

            // 执行请求
            try (CloseableHttpResponse response = httpClient.execute(httpGet)) {

                int statusCode = response.getStatusLine().getStatusCode();

                if (statusCode == 200) {
                    HttpEntity entity = response.getEntity();

                    if (entity != null) {
                        // 检查内容类型
                        String contentType = entity.getContentType().getValue();
                        if (contentType == null || !contentType.startsWith("image/")) {
                            throw new IOException("不是图片类型: " + contentType);
                        }

                        // 下载文件
                        try (InputStream inputStream = entity.getContent();
                             FileOutputStream outputStream = new FileOutputStream(savePath.toFile())) {

                            byte[] buffer = new byte[4096];
                            int bytesRead;
                            while ((bytesRead = inputStream.read(buffer)) != -1) {
                                outputStream.write(buffer, 0, bytesRead);
                            }
                        }

                        return true;
                    }
                } else {
                    throw new IOException("HTTP请求失败，状态码: " + statusCode);
                }
            }
        }

        return false;
    }

    /**
     * 从URL生成文件名
     */
    private String generateFileName(String imageUrl) {
        return extractFileName(imageUrl);
    }

    /**
     * 从 URL 提取文件名（解码、去参数、清理非法字符），供外部复用。
     */
    public static String extractFileName(String imageUrl) {
        try {
            // 解码URL
            String decodedUrl = URLDecoder.decode(imageUrl, "UTF-8");

            // 获取最后一个/之后的内容
            int lastSlashIndex = decodedUrl.lastIndexOf('/');
            String fileName = (lastSlashIndex >= 0) ?
                    decodedUrl.substring(lastSlashIndex + 1) : decodedUrl;

            // 移除URL参数
            int paramIndex = fileName.indexOf('?');
            if (paramIndex > 0) {
                fileName = fileName.substring(0, paramIndex);
            }

            // 如果文件名为空或没有扩展名，生成新文件名
            if (fileName.isEmpty() || !fileName.contains(".")) {
                String extension = getFileExtensionFromUrl(imageUrl);
                return UUID.randomUUID().toString() + extension;
            }

            // 清理文件名中的非法字符
            fileName = fileName.replaceAll("[\\\\/:*?\"<>|]", "_");

            return fileName;

        } catch (Exception e) {
            // 如果处理失败，生成随机文件名
            return UUID.randomUUID().toString() + ".jpg";
        }
    }

    /**
     * 从URL获取文件扩展名
     */
    private static String getFileExtensionFromUrl(String imageUrl) {
        String urlLower = imageUrl.toLowerCase();
        if (urlLower.contains(".jpg") || urlLower.contains(".jpeg")) {
            return ".jpg";
        } else if (urlLower.contains(".png")) {
            return ".png";
        } else if (urlLower.contains(".gif")) {
            return ".gif";
        } else if (urlLower.contains(".webp")) {
            return ".webp";
        } else if (urlLower.contains(".bmp")) {
            return ".bmp";
        }
        return ".jpg";
    }

    /**
     * 生成可访问的URL
     */
    private String generateAccessUrl(String fileName) {
        return BASE_URL + "/pic/" + fileName;
    }

    /**
     * 批量下载图片
     */
    public Map<String, String> batchDownloadImages(Map<String, String> imageUrlMap) {
        Map<String, String> results = new HashMap<>();

        for (Map.Entry<String, String> entry : imageUrlMap.entrySet()) {
            String key = entry.getKey();
            String url = entry.getValue();

            try {
                String imageUrl = getImageUrl(url, null);
                results.put(key, imageUrl);
            } catch (Exception e) {
                results.put(key, "下载失败: " + e.getMessage());
            }
        }

        return results;
    }

    /**
     * 检查图片是否存在
     */
    public boolean checkImageExists(String fileName) {
        Path filePath = Paths.get(getImgPath(), fileName);
        return Files.exists(filePath);
    }

    /**
     * 删除图片
     */
    public boolean deleteImage(String fileName) {
        try {
            Path filePath = Paths.get(getImgPath(), fileName);
            return Files.deleteIfExists(filePath);
        } catch (IOException e) {
            return false;
        }
    }

    public String getImgPath() {
        String osName = System.getProperty("os.name").toLowerCase();

        if (osName.contains("win")) {
            return IMAGE_STORAGE_PATH;
        } else if (osName.contains("nix") || osName.contains("nux") || osName.contains("aix")) {
            return IMAGE_STORAGE_PATH_FNOS;
        }else {
            return IMAGE_STORAGE_PATH_FNOS;
        }
    }
}
