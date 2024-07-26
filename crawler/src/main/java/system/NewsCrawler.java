package system;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.opencsv.CSVWriter;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import io.github.bonigarcia.wdm.WebDriverManager;
import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;

import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

public class NewsCrawler {
	private static final Logger logger = LoggerFactory.getLogger(NewsCrawler.class);

	public static void main(String[] args) {
		String url = "https://www.lanacion.com.ar/tema/videojuegos-tid48572/"; // URL de la página que quieres crawlear
		String csvFolder = "../data/raw"; // Nombre de la carpeta donde se guardará el archivo CSV
		String csvFile = "news.csv"; // Nombre del archivo CSV donde se guardarán las noticias

		WebDriverManager.chromedriver().driverVersion("127.0.0").setup();
		ChromeOptions options = new ChromeOptions();
		options.addArguments("--lang=en");
		ChromeDriver driver = new ChromeDriver(options);

		try {
			// Crear el directorio si no existe
			Path directory = Paths.get(csvFolder).toAbsolutePath().normalize();
			if (!Files.exists(directory)) {
				Files.createDirectories(directory);
				logger.info("Directorio creado en: {}", directory);
			} else {
				logger.info("El directorio ya existe en: {}", directory);
			}

			// Preparar la ruta completa al archivo CSV
			Path filePath = Paths.get(csvFolder, csvFile).toAbsolutePath().normalize();
			logger.info("El archivo se guardará en: {}", filePath);

			// Preparar el archivo CSV para escritura
			try (CSVWriter csvWriter = new CSVWriter(new FileWriter(filePath.toString()))) {
				// Navegar a la página
				driver.get(url);

				// Escribir encabezados al archivo CSV
				String[] headers = { "titulo", "link", "cuerpo" };
				csvWriter.writeNext(headers);
				logger.info("Añadiendo los headers: '{}' al archivo {}", (Object) headers, csvFile);

				// Variables para el control del loop
				boolean hasMoreNews = true;

				// Definir el límite de clics
				int maxClicks = 5; // Cambia este valor según tus necesidades
				int clickCount = 0; // Contador de clics

				while (hasMoreNews && clickCount < maxClicks) {
					// Obtener el contenido de la página actual con Jsoup
					Document doc = Jsoup.parse(driver.getPageSource());
					Elements articlesGames = doc.select("div.row-gap-tablet-3 section.mod-description");

					// Iterar sobre los artículos y escribir los datos al CSV
					for (Element article : articlesGames) {
						if (article.select("span.badge.--sixxs.--arial.com-label.--exclusive-ln").isEmpty()) {
							Elements txtLink = article
									.select("h2.com-title.--font-primary.--l.--font-medium a.com-link");
							String link = "https://www.lanacion.com.ar" + txtLink.attr("href");

							logger.info("Entrando a la noticia del link {}", link);
							Document news = Jsoup.connect(link).get();
							String title = news.select("h1.com-title.--font-primary.--sixxl.--font-extra").text(); // Suponiendo
																													// que
																													// el
																													// título
																													// está
																													// en
																													// un
																													// <h1>
							Elements parrafos = news.select("p.com-paragraph.--s");
							String cuerpo = parrafos.text(); // Suponiendo que el cuerpo está en un <p>

							String[] data = { title, link, cuerpo };
							csvWriter.writeNext(data);
							logger.info("Añadiendo los datos al archivo {}", csvFile);
						} else {
							logger.info("Se detectó una noticia exclusiva para miembros. Se saltea la noticia.");
						}

					}
					// Intentar encontrar el botón "Cargar más" y hacer clic

					// Intentar encontrar el botón "Cargar más" y hacer clic
					List<WebElement> loadMoreButtons = driver
							.findElements(By.cssSelector("button.com-button.--secondary"));
					if (loadMoreButtons.isEmpty()) {
						hasMoreNews = false;
					} else {
						// Desplazarse a la vista del botón
						((JavascriptExecutor) driver).executeScript("arguments[0].scrollIntoView(true);",
								loadMoreButtons.get(0));

						// Hacer clic en el botón
						loadMoreButtons.get(0).click();
						clickCount++;
						// Esperar un momento para que se carguen más noticias
						try {
							Thread.sleep(300); // Ajusta este tiempo según sea necesario
						} catch (InterruptedException e) {
							e.printStackTrace();
						}
					}

				}

				logger.info("Datos guardados en {}", filePath);
			}
		} catch (IOException e) {
			logger.error("Error al escribir en el archivo CSV", e);
		} finally {
			// Cerrar el navegador
			driver.quit();
		}
	}
}
