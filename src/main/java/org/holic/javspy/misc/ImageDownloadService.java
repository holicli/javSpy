package org.holic.javspy.misc;

import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Value;

import javax.annotation.PostConstruct;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Service
public class ImageDownloadService {

    /** SOCKS5 代理主机（复用 javbus-api 代理配置，下载 javbus 封面用） */
    @Value("${conf.javbus-api.proxy-host:}")
    private String proxyHost;

    /** 代理总开关：false 时即使配置了 host/port 也不会走代理 */
    @Value("${conf.javbus-api.proxy-enabled:false}")
    private boolean proxyEnabled;

    /** SOCKS5 代理端口 */
    @Value("${conf.javbus-api.proxy-port:0}")
    private int proxyPort;

    /** 图片存储目录（application.yml 的 conf.image.storage-path，服务器上建议配置绝对路径） */
    @Value("${conf.image.storage-path:../pic}")
    private String imageStoragePath;

    private OkHttpClient httpClient;

    @PostConstruct
    public void initImageHttpClient() {
        OkHttpClient.Builder builder = new OkHttpClient.Builder()
                .connectTimeout(CONNECT_TIMEOUT, TimeUnit.MILLISECONDS)
                .readTimeout(SOCKET_TIMEOUT, TimeUnit.MILLISECONDS)
                .writeTimeout(SOCKET_TIMEOUT, TimeUnit.MILLISECONDS)
                .followRedirects(true);
        if (proxyEnabled && StringUtils.isNotBlank(proxyHost) && proxyPort > 0) {
            builder.proxy(new Proxy(Proxy.Type.SOCKS, new InetSocketAddress(proxyHost, proxyPort)));
            System.out.println("图片下载走 SOCKS5 代理: " + proxyHost + ":" + proxyPort);
        }
        this.httpClient = builder.build();
    }

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
        if (Files.isRegularFile(localFilePath) && Files.size(localFilePath) > 0) {
            return generateAccessUrl(fileName);
        }

        boolean downloadSuccess;
        try {
            downloadSuccess = downloadImage(imageUrl, localFilePath, referer);
        } catch (IOException | IllegalArgumentException e) {
            System.err.println("下载图片失败: " + imageUrl + " (" + e.getMessage() + ")");
            return null;
        }

        if (downloadSuccess) {
            return generateAccessUrl(fileName);
        } else {
            throw new IOException("图片下载失败: " + imageUrl);
        }
    }

    /** 缓存详情图片，避免同名缩略图、大图或不同影片的图片互相覆盖。 */
    public String getCachedImageUrl(String source, String referer) throws IOException {
        if (StringUtils.isBlank(source)) {
            return null;
        }
        String url = normalizeAccessUrl(source.trim());
        if (url.startsWith("/pic/")) {
            String fileName = extractFileName(url);
            Path file = Paths.get(getImgPath(), fileName);
            return Files.isRegularFile(file) && Files.size(file) > 0 ? generateAccessUrl(fileName) : null;
        }

        HttpUrl imageUrl = HttpUrl.get(referer).resolve(url);
        if (imageUrl == null) {
            return null;
        }
        String fileName = "detail-" + UUID.nameUUIDFromBytes(imageUrl.toString().getBytes(StandardCharsets.UTF_8))
                + getFileExtensionFromUrl(imageUrl.encodedPath());
        return getImageUrl(imageUrl.toString(), fileName, referer);
    }

    /**
     * 使用 OkHttp 下载图片（支持 SOCKS5 代理 + Referer 防盗链）。
     */
    private boolean downloadImage(String imageUrl, Path savePath, String referer) throws IOException {
        Request.Builder builder = new Request.Builder()
                .url(imageUrl)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .header("Accept", "image/webp,image/apng,image/*,*/*;q=0.8");
        if (referer != null && !referer.trim().isEmpty()) {
            builder.header("Referer", referer);
        }
        try (Response response = httpClient.newCall(builder.build()).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("HTTP请求失败，状态码: " + response.code());
            }
            ResponseBody body = response.body();
            if (body == null) {
                return false;
            }
            MediaType mediaType = body.contentType();
            String contentType = mediaType == null ? null : mediaType.type() + "/" + mediaType.subtype();
            if (contentType == null || !contentType.startsWith("image/")) {
                throw new IOException("不是图片类型: " + contentType);
            }
            Path temporaryFile = Files.createTempFile(savePath.getParent(), ".download-", ".tmp");
            try {
                try (InputStream inputStream = body.byteStream()) {
                    if (Files.copy(inputStream, temporaryFile, StandardCopyOption.REPLACE_EXISTING) == 0) {
                        return false;
                    }
                }
                try {
                    Files.move(temporaryFile, savePath, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
                } catch (AtomicMoveNotSupportedException e) {
                    Files.move(temporaryFile, savePath, StandardCopyOption.REPLACE_EXISTING);
                }
                return true;
            } finally {
                Files.deleteIfExists(temporaryFile);
            }
        }
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
        return "/pic/" + fileName;
    }

    /**
     * 把旧版本写入的 127.0.0.1 / localhost 绝对图片地址统一转成相对路径，
     * 部署到服务器后浏览器会按当前站点域名访问图片。
     */
    public static String normalizeAccessUrl(String url) {
        if (StringUtils.isBlank(url)) {
            return url;
        }
        return url.replaceAll("(?i)^https?://(127\\.0\\.0\\.1|localhost)(?::\\d+)?/pic/", "/pic/");
    }

    /**
     * 批量下载图片
     */
    public List<ImageDownloadResult> batchDownloadImages(List<ImageDownloadTask> tasks) {
        List<ImageDownloadResult> results = new ArrayList<>();

        for (ImageDownloadTask task : tasks) {
            String key = task.getKey();
            String url = task.getUrl();
            ImageDownloadResult result = new ImageDownloadResult();
            result.setKey(key);
            try {
                String imageUrl = getImageUrl(url, null);
                result.setSuccess(true);
                result.setUrl(imageUrl);
            } catch (Exception e) {
                result.setSuccess(false);
                result.setMessage("下载失败: " + e.getMessage());
            }
            results.add(result);
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
        return imageStoragePath;
    }
}
