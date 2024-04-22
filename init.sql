-- Creating the Auditorium table
CREATE TABLE auditorium (
                            id BIGINT AUTO_INCREMENT PRIMARY KEY,
                            number VARCHAR(255)
);

-- Creating the Group table
CREATE TABLE groups_table (
                              id BIGINT AUTO_INCREMENT PRIMARY KEY,
                              name VARCHAR(255),
                              auditorium_id BIGINT NOT NULL,
                              FOREIGN KEY (auditorium_id) REFERENCES auditorium(id)
);

-- Creating the Instructor table
CREATE TABLE instructor (
                            id BIGINT AUTO_INCREMENT PRIMARY KEY,
                            name VARCHAR(255)
);

-- Creating the Subject table
CREATE TABLE subject (
                         id BIGINT AUTO_INCREMENT PRIMARY KEY,
                         name VARCHAR(255)
);

-- Creating the Subject-Instructor relationship table
CREATE TABLE subject_instructor (
                                    subject_id BIGINT,
                                    instructor_id BIGINT,
                                    PRIMARY KEY (subject_id, instructor_id),
                                    FOREIGN KEY (subject_id) REFERENCES subject(id),
                                    FOREIGN KEY (instructor_id) REFERENCES instructor(id)
);

-- Creating the Schedule table
CREATE TABLE schedule (
                          id BIGINT AUTO_INCREMENT PRIMARY KEY,
                          group_id BIGINT NOT NULL,
                          auditorium_id BIGINT NOT NULL,
                          subject_id BIGINT NOT NULL,
                          instructor_id BIGINT NOT NULL,
                          day_of_week VARCHAR(255) NOT NULL,
                          num_subgroup INT NOT NULL,
                          week_number INT NOT NULL,
                          start_time VARCHAR(255) NOT NULL,
                          end_time VARCHAR(255) NOT NULL,
                          FOREIGN KEY (group_id) REFERENCES groups_table(id),
                          FOREIGN KEY (auditorium_id) REFERENCES auditorium(id),
                          FOREIGN KEY (subject_id) REFERENCES subject(id),
                          FOREIGN KEY (instructor_id) REFERENCES instructor(id)
);
