package org.holic.javspy.misc;

import lombok.Data;

/**
 * 批量图片下载结果。
 */
@Data
public class ImageDownloadResult {

    private String key;
    private boolean success;
    private String url;
    private String message;
}
