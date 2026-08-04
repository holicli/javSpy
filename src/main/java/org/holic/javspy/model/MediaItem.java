package org.holic.javspy.model;

import com.alibaba.fastjson.annotation.JSONField;
import com.fasterxml.jackson.annotation.JsonProperty;

public class MediaItem {
    @JSONField(name = "RunTimeTicks")
    private Long runTimeTicks;

    @JSONField(name = "Type")
    private String type;

    @JSONField(name = "ServerId")
    private String serverId;

    @JSONField(name = "BackdropImageTags")
    private String[] backdropImageTags;

    @JSONField(name = "MediaType")
    private String mediaType;

    @JSONField(name = "Id")
    private String id;

    @JSONField(name = "IsFolder")
    private Boolean isFolder;

    @JSONField(name = "ImageTags")
    private ImageTags imageTags;

    @JSONField(name = "Name")
    private String name;
}
