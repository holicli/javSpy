package org.holic.javspy.misc;

import lombok.Data;

/**
 * 批量图片下载任务。
 */
@Data
public class ImageDownloadTask {

    private String key;
    private String url;
}
