package ru.practicum.ewm.main.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.practicum.ewm.main.model.UserEntity;

public interface UserRepository extends JpaRepository<UserEntity, Long> {
}
