package com.hotelplatform.hotel.mapper;

import com.hotelplatform.hotel.dto.HotelCreateRequest;
import com.hotelplatform.hotel.dto.HotelDto;
import com.hotelplatform.hotel.dto.HotelUpdateRequest;
import com.hotelplatform.hotel.entity.Hotel;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

import java.util.List;

@Mapper(componentModel = "spring")
public interface HotelMapper {

    @Mapping(target = "roomsCount", expression = "java(hotel.getRooms() == null ? 0 : hotel.getRooms().size())")
    HotelDto toDto(Hotel hotel);

    List<HotelDto> toDtoList(List<Hotel> hotels);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "rooms", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    Hotel toEntity(HotelCreateRequest request);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "rooms", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    void update(HotelUpdateRequest request, @MappingTarget Hotel hotel);
}
