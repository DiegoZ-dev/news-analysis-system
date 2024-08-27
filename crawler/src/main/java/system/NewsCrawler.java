package system;

<<<<<<< HEAD
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
import org.openqa.selenium.StaleElementReferenceException;
import org.openqa.selenium.TimeoutException;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
=======
>>>>>>> f96441f (Se numeran las noticias en los logs, se añade uso de esperas explicitas de selenium, se añade un makefile para correr el crawler y el notebook de analisis)
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;

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
		String csvFolder = "../data/raw";
		String csvFile = "news.csv";

		WebDriverManager.chromedriver().driverVersion("127.0.6533.72").setup();
		ChromeOptions options = new ChromeOptions();
		options.addArguments("--lang=en");
		options.addArguments("--headless");
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

			try (CSVWriter csvWriter = new CSVWriter(new FileWriter(filePath.toString()))) {
				driver.get(url);

				String[] headers = { "titulo", "link", "cuerpo" };
				csvWriter.writeNext(headers);
				logger.info("Añadiendo los headers: '{}' al archivo {}", headers, csvFile);

				WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(10));
				int maxClicks = 1000;
                boolean exist = true;
				for (int clickCount = 0; clickCount < maxClicks && exist; clickCount++) {
					try {
						// Espera explícita para encontrar el botón y asegurarse de que esté visible y
						// clickeable
						WebElement loadMoreButton = wait.until(ExpectedConditions
								.elementToBeClickable(By.cssSelector("button.com-button.--secondary")));
						((JavascriptExecutor) driver).executeScript("arguments[0].scrollIntoView(true);",
								loadMoreButton);
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
				
				for (Element article : articlesGames) {
					if (article.select("span.badge.--sixxs.--arial.com-label.--exclusive-ln").isEmpty()) {
						newsCount++;
						Elements txtLink = article.select("h2.com-title.--font-primary.--l.--font-medium a.com-link");
						String link = "https://www.lanacion.com.ar" + txtLink.attr("href");

						logger.info("Noticia #{} - Entrando a la noticia del link {}", newsCount, link);
						Document news = Jsoup.connect(link).get();

						String title = news.select("h1.com-title.--font-primary.--sixxl.--font-extra").text();
						Elements parrafos = news.select("p.com-paragraph.--s");
						String cuerpo = parrafos.text();
						String[] data = { title, link, cuerpo };

						csvWriter.writeNext(data);
						logger.info("Añadiendo los datos al archivo {}", csvFile);
					} else {
						logger.info("Se detectó una noticia exclusiva para miembros. Se saltea la noticia.");
					}
				}

				logger.info("Datos guardados en {}", filePath);
			}
		} catch (IOException e) {
      logger.error("Error al escribir/abrir en el archivo CSV");
    } catch (TimeoutException time) {
      logger.error("Se agotó el tiempo de espera del servidor.");
    } catch (StaleElementReferenceException e) {
      logger.error("El botón se movió de lugar o no se alcanzó a presionar el botón con el tiempo disponible para cargar el botón");
    } finally {
      // Cerrar el navegador
      driver.quit();
    }
  }
}
