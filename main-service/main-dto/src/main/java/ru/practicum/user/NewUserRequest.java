package ru.practicum.user;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.validator.constraints.Length;

import javax.validation.constraints.Email;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class NewUserRequest {
    @NotNull
    @Email
    @Length(min = 6, max = 254, message = "User email length must be between 6 and 254 characters")
    private String email;
    @NotBlank
    @Length(min = 2, max = 250, message = "User name length must be between 2 and 250 characters")
    private String name;
}