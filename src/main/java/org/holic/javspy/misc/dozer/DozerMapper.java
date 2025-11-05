package org.holic.javspy.misc.dozer;

import com.github.dozermapper.core.Mapper;
import com.github.dozermapper.core.MappingException;
import com.google.common.collect.Lists;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * 扩展Dozer Mapper，使其支持集合、数组等
 * 使用：
 * dozerMapper.mapCollection(src, dest);
 * @author Administrator
 */
@AllArgsConstructor
@Component("dozerExMapper")
public class DozerMapper {

    /** Dozer转换对象 */
    private final Mapper mapper;

    /**
     * Constructs new instance of destinationClass and performs mapping between from source
     *
     * @param source           object to convert from
     * @param destinationClass type to convert to
     * @param mapId            id in configuration for mapping
     * @param <T>              type to convert to
     * @return mapped object
     * @throws MappingException mapping failure
     */
    public <T> T map(Object source, Class<T> destinationClass, String mapId) throws MappingException {
        if(Objects.isNull(source)) {
            return null;
        }
        return mapper.map(source, destinationClass, mapId);
    }

    /**
     * Constructs new instance of destinationClass and performs mapping between from source
     *
     * @param source           object to convert from
     * @param destinationClass type to convert to
     * @param <T>              type to convert to
     * @return mapped object
     * @throws MappingException mapping failure
     */
    public <T> T map(Object source, Class<T> destinationClass) throws MappingException {
        if(Objects.isNull(source)) {
            return null;
        }
        return mapper.map(source, destinationClass);
    }

    /**
     * Performs mapping between source and destination objects
     *
     * @param source      object to convert from
     * @param destination object to convert to
     * @param mapId       id in configuration for mapping
     * @throws MappingException mapping failure
     */
    public void map(Object source, Object destination, String mapId) throws MappingException {
        mapper.map(source, destination, mapId);
    }

    /**
     * Performs mapping between source and destination objects
     *
     * @param source      object to convert from
     * @param destination object to convert to
     * @throws MappingException mapping failure
     */
    public void map(Object source, Object destination) throws MappingException {
        mapper.map(source, destination);
    }

    public <T> List<T> mapList(Object[] source, Class<T> destinationClass) throws MappingException {
        return mapList(Arrays.asList(source), destinationClass);
    }

    public <T> List<T> mapList(Object[] source, List<T> destination, Class<T> destinationClass) throws MappingException {
        return mapList(Arrays.asList(source), destination, destinationClass);
    }

    public <T> List<T> mapList(List<?> source, Class<T> destinationClass) throws MappingException {
        return mapList(source, null, destinationClass);
    }

    public <T> List<T> mapList(List<?> source, List<T> destination, Class<T> destinationClass) throws MappingException {
        if(Objects.isNull(source)) {
            return null;
        }

        if(Objects.isNull(destination)) {
            destination = Lists.newArrayList();
        }

        for(Object sourceObj : source) {
            destination.add(map(sourceObj, destinationClass));
        }
        return destination;
    }
}
