package com.hotelplatform.booking.mapper;

import com.hotelplatform.booking.dto.UserDto;
import com.hotelplatform.booking.entity.UserAccount;
import org.mapstruct.Mapper;

import java.util.List;

@Mapper(componentModel = "spring")
public interface UserMapper {

    UserDto toDto(UserAccount user);

    List<UserDto> toDtoList(List<UserAccount> users);
}
