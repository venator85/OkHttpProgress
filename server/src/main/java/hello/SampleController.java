package hello;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.context.embedded.EmbeddedServletContainerCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import java.io.IOException;
import java.util.Random;

import javax.servlet.ServletInputStream;
import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

@Controller
@EnableAutoConfiguration
public class SampleController {

	@Bean
	public EmbeddedServletContainerCustomizer containerCustomizer() {
		return container -> container.setPort(8088);
	}

	@RequestMapping("/hello")
	@ResponseBody
	void home(HttpServletRequest request, HttpServletResponse response) throws IOException {
		byte[] buf = new byte[1024];

		ServletInputStream in = request.getInputStream();
		int read, total = 0;
		while ((read = in.read(buf)) != -1) {
			total += read;
		}

		System.out.println("letta tutta la request: " + total + " bytes");

		int SIZE = 5 * 1024 * 1024;

		response.setContentLength(SIZE);

		ServletOutputStream out = response.getOutputStream();
		Random r = new Random();
		for (int i = 0; i < SIZE; i += buf.length) {
			r.nextBytes(buf);
			out.write(buf);
		}
		out.close();

		response.setStatus(200);

		System.out.println("inviata la response");
	}

	public static void main(String[] args) throws Exception {
		SpringApplication.run(SampleController.class, args);
	}
}
