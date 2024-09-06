package system;

import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Vector;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.opencsv.CSVWriter;

import io.github.bonigarcia.wdm.WebDriverManager;

public class NewsCrawler {
	private static final Logger logger = LoggerFactory.getLogger(NewsCrawler.class);

	public static void main(String[] args) {
		String url = "https://www.lanacion.com.ar/tema/videojuegos-tid48572/";
		String csvFolder = "data/raw";
		String csvFile = "news.csv";

		WebDriverManager.chromedriver().driverVersion("128").setup();
		ChromeOptions options = new ChromeOptions();
		options.addArguments("--lang=en");
		options.addArguments("--headless");
		options.addArguments("--disable-gpu");
		options.addArguments("--no-sandbox");
		options.addArguments("--disable-dev-shm-usage");
		options.addArguments("--disable-extensions");
		WebDriver driver = new ChromeDriver(options);

		try {
			Path directory = Paths.get(csvFolder).toAbsolutePath().normalize();
			if (!Files.exists(directory)) {
				Files.createDirectories(directory);
				logger.info("Directorio creado en: {}", directory);
			} else {
				logger.info("El directorio ya existe en: {}", directory);
			}

			Path filePath = Paths.get(csvFolder, csvFile).toAbsolutePath().normalize();
			logger.info("El archivo se guardará en: {}", filePath);

			// Preparar el archivo CSV para escritura
			try (CSVWriter csvWriter = new CSVWriter(new FileWriter(filePath.toString()))) {
				driver.get(url);
				String[] headers = { "titulo", "fecha", "link", "cuerpo" };
				csvWriter.writeNext(headers);
				logger.info("Añadiendo los headers: '{}' al archivo {}", headers, csvFile);

				WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(10));
				int maxClicks = 0;
				boolean exist = true;
				for (int clickCount = 0; clickCount < maxClicks && exist; clickCount++) {
					try {
						WebElement loadMoreButton = wait.until(ExpectedConditions.elementToBeClickable(By.cssSelector("button.com-button.--secondary")));
						((JavascriptExecutor) driver).executeScript("arguments[0].scrollIntoView(true);",loadMoreButton);
						loadMoreButton.click();
						logger.info("Haciendo clic en el botón 'Cargar más'. Click número: {}", clickCount + 1);

						wait.until(ExpectedConditions.invisibilityOfElementLocated(By.cssSelector(".loading-spinner")));
					} catch (Exception e) {
						logger.info("No se encontró el botón 'Cargar más'. Finalizando la carga de resultados.");
						exist = false;
					}
				}

				Document doc = Jsoup.parse(driver.getPageSource());
				Elements articlesGames = doc.select("div.row-gap-tablet-3 section.mod-description");

				int newsCount = 0;
				Vector<String[]> datos = new Vector<>(1000, 1000);
				DateTimeFormatter formatter = DateTimeFormatter.ofPattern("d-MMMM-yyyy");

				for (Element article : articlesGames) {
					if (article.select("span.badge.--sixxs.--arial.com-label.--exclusive-ln").isEmpty()) {
						newsCount++;
						Elements txtLink = article.select("h2.com-title.--font-primary.--l.--font-medium a.com-link");
						String link = "https://www.lanacion.com.ar" + txtLink.attr("href");

						logger.info("Noticia #{} - Entrando a la noticia del link {}", newsCount, link);
						Document news = Jsoup.connect(link).get();

						String title = news.select("h1.com-title.--font-primary.--sixxl.--font-extra").text();
						String fechaAFormatear = news.select("time.com-date").attr("datetime");
						String cuerpo = news.select("p.com-paragraph.--s").text();
						String[] fechaAFormatearA = fechaAFormatear.split(" de ");

						if (fechaAFormatearA.length == 3) {
							String fechaFormateada = fechaAFormatearA[0] + "-" + fechaAFormatearA[1] + "-"+ fechaAFormatearA[2];

							try {
								LocalDate fecha = LocalDate.parse(fechaFormateada, formatter);
								String[] bDatos = { title, fecha.toString(), link, cuerpo };
								datos.add(bDatos);
								logger.info("Añadiendo los datos al arreglo.");
							} catch (DateTimeParseException e) {
								logger.error("Error al parsear la fecha: {}", e.getMessage());
							}
						} else {
							logger.warn("Formato de fecha inesperado: {}", fechaAFormatear);
						}
					} else {
						logger.info("Se detectó una noticia exclusiva para miembros. Se saltea la noticia.");
					}
				}

				logger.info("Añadiendo todas las noticias obtenidas al archivo");
				csvWriter.writeAll(datos);
				logger.info("Datos guardados en {}", filePath);
			}
		} catch (IOException e) {
			logger.error("Error al escribir/abrir en el archivo CSV", e);
		} catch (Exception e) {
			logger.error("Error durante la ejecución: ", e);
		} finally {
			driver.quit();
		}
	}
}
