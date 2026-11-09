package com.hotelplatform.booking.mapper;

import com.hotelplatform.booking.dto.BookingDto;
import com.hotelplatform.booking.entity.Booking;
import org.mapstruct.Mapper;

import java.util.List;

@Mapper(componentModel = "spring")
public interface BookingMapper {

    BookingDto toDto(Booking booking);

    List<BookingDto> toDtoList(List<Booking> bookings);
}
