package com.hotelplatform.hotel.config;

import com.hotelplatform.hotel.entity.Hotel;
import com.hotelplatform.hotel.entity.Room;
import com.hotelplatform.hotel.repository.HotelRepository;
import com.hotelplatform.hotel.repository.RoomRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import java.math.BigDecimal;

// демо-данные: 3 отеля и номера с разным times_booked, чтобы рекомендации были наглядными
@Configuration
@RequiredArgsConstructor
@Slf4j
public class DataSeeder {

    @Bean
    @Profile("!test")
    CommandLineRunner seedHotels(HotelRepository hotelRepository, RoomRepository roomRepository) {
        return args -> {
            if (hotelRepository.count() > 0) {
                return;
            }
            Hotel grand = hotelRepository.save(Hotel.builder()
                    .name("Grand Moscow").address("ул. Тверская, 1").city("Москва").build());
            Hotel plaza = hotelRepository.save(Hotel.builder()
                    .name("Piter Plaza").address("Невский проспект, 28").city("Санкт-Петербург").build());
            Hotel sunset = hotelRepository.save(Hotel.builder()
                    .name("Sochi Sunset").address("ул. Черноморская, 7").city("Сочи").build());

            // (номер, цена, available, timesBooked) — разброс times_booked для демонстрации рекомендаций
            seedRoom(roomRepository, grand, "101", "5500.00", true, 7);
            seedRoom(roomRepository, grand, "102", "5500.00", true, 7);
            seedRoom(roomRepository, grand, "103", "7800.00", true, 3);
            seedRoom(roomRepository, grand, "104", "12000.00", false, 0); // в ремонте
            seedRoom(roomRepository, plaza, "201", "4200.00", true, 2);
            seedRoom(roomRepository, plaza, "202", "4200.00", true, 5);
            seedRoom(roomRepository, plaza, "203", "6400.00", true, 0);
            seedRoom(roomRepository, plaza, "204", "9100.00", true, 1);
            seedRoom(roomRepository, sunset, "301", "3600.00", true, 0);
            seedRoom(roomRepository, sunset, "302", "3600.00", true, 4);
            seedRoom(roomRepository, sunset, "303", "5900.00", true, 6);
            seedRoom(roomRepository, sunset, "304", "8800.00", true, 0);

            log.info("Предзаполнено: {} отелей, {} номеров", hotelRepository.count(), roomRepository.count());
        };
    }

    private void seedRoom(RoomRepository rooms, Hotel hotel, String number, String price,
                          boolean available, int timesBooked) {
        rooms.save(Room.builder()
                .hotel(hotel)
                .number(number)
                .pricePerNight(new BigDecimal(price))
                .available(available)
                .timesBooked(timesBooked)
                .build());
    }
}
