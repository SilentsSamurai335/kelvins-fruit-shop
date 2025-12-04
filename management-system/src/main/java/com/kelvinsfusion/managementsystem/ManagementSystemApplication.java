package com.kelvinsfusion.managementsystem;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import jakarta.annotation.PostConstruct; // Import this!
import java.util.TimeZone;             // Import this!

@SpringBootApplication
public class ManagementSystemApplication {

	public static void main(String[] args) {
		SpringApplication.run(ManagementSystemApplication.class, args);
	}

	// --- NEW: FORCE KENYA TIMEZONE ---
	@PostConstruct
	public void init() {
		// This forces the app to use Nairobi time, even if the server is in Singapore
		TimeZone.setDefault(TimeZone.getTimeZone("Africa/Nairobi"));
		System.out.println("✅ App running in Timezone: " + TimeZone.getDefault().getID());
	}
}