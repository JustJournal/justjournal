package com.justjournal.services;

import com.justjournal.model.UserImage;
import com.justjournal.repository.UserImageRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.context.ActiveProfiles;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@ActiveProfiles("test")
class UserImageServiceTests {

    @Mock
    private UserImageRepository userImageRepository;

    @InjectMocks
    private UserImageService userImageService;

    @Test
    void getUserImages_shouldReturnListOfUserImages() {
        // Arrange
        String username = "testuser";
        UserImage image1 = new UserImage();
        image1.setId(1);
        image1.setTitle("Image 1");

        UserImage image2 = new UserImage();
        image2.setId(2);
        image2.setTitle("Image 2");

        List<UserImage> expectedImages = Arrays.asList(image1, image2);

        when(userImageRepository.findByUsernameOrderByTitleTitleAsc(username)).thenReturn(expectedImages);

        // Act
        List<UserImage> result = userImageService.getUserImages(username);

        // Assert
        assertNotNull(result);
        assertEquals(2, result.size());
        assertEquals(expectedImages, result);

        verify(userImageRepository, times(1)).findByUsernameOrderByTitleTitleAsc(username);
    }

    @Test
    void getUserImages_shouldReturnEmptyList_whenNoImagesFound() {
        // Arrange
        String username = "emptyuser";
        when(userImageRepository.findByUsernameOrderByTitleTitleAsc(username)).thenReturn(List.of());

        // Act
        List<UserImage> result = userImageService.getUserImages(username);

        // Assert
        assertNotNull(result);
        assertTrue(result.isEmpty());

        verify(userImageRepository, times(1)).findByUsernameOrderByTitleTitleAsc(username);
    }

    @Test
    void getUserImages_shouldThrowException_whenRepositoryThrowsException() {
        // Arrange
        String username = "erroruser";
        when(userImageRepository.findByUsernameOrderByTitleTitleAsc(username)).thenThrow(new RuntimeException("Database error"));

        // Act & Assert
        assertThrows(RuntimeException.class, () -> userImageService.getUserImages(username));

        verify(userImageRepository, times(1)).findByUsernameOrderByTitleTitleAsc(username);
    }
}