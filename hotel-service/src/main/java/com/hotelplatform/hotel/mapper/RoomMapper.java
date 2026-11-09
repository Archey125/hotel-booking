package com.hotelplatform.hotel.mapper;

import com.hotelplatform.hotel.dto.RoomDto;
import com.hotelplatform.hotel.entity.Room;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

@Mapper(componentModel = "spring")
public interface RoomMapper {

    @Mapping(target = "hotelId", source = "hotel.id")
    @Mapping(target = "hotelName", source = "hotel.name")
    RoomDto toDto(Room room);

    List<RoomDto> toDtoList(List<Room> rooms);
}
