package org.holic.javspy.model;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

/**
 * 磁力链接实体类
 * 对应接口返回的磁力链接信息
 */
@Data
public class MagnetResponse {

    private List<Magnet> magnet;

}
