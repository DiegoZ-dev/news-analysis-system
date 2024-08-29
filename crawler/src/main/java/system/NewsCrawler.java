package system;

import java.io.FileWriter;
import java.io.IOException;
import java.util.Vector;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

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
import org.openqa.selenium.StaleElementReferenceException;
import org.openqa.selenium.TimeoutException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.dockerjava.api.model.DriverStatus;
import com.opencsv.CSVWriter;

import io.github.bonigarcia.wdm.WebDriverManager;

public class NewsCrawler {
  private static final Logger logger = LoggerFactory.getLogger(NewsCrawler.class);

  public static void main(String[] args) {
    String url = "https://www.lanacion.com.ar/tema/videojuegos-tid48572/";
    String csvFolder = "../data/raw";
    String csvFile = "news.csv";

    WebDriverManager.chromedriver().driverVersion("128").setup();
    ChromeOptions options = new ChromeOptions();
    options.addArguments("--lang=en");
    options.addArguments("--headless");
    //Las siguientes opciones es para que Selenium inicie más rápido
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
        // Navegar a la página
        logger.info("Conectandose a la página {}", url);
        driver.get(url);
        String[] headers = { "titulo","fecha", "link", "cuerpo" };
        csvWriter.writeNext(headers);
        logger.info("Añadiendo los headers: '{}' al archivo {}", headers, csvFile);
        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(10));
        int maxClicks = 1000;
        boolean exist = true;
        for (int clickCount = 0; clickCount < maxClicks && exist; clickCount++) {
          try {
            // Espera explícita para encontrar el botón y asegurarse de que esté visible y
            // clickeable
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
        DateTimeFormatter fechaFormatoEntrada = DateTimeFormatter.ofPattern("ddMMMMyyyy");
        
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
            String fechaAFormatearA[] = fechaAFormatear.split(" de ");

            LocalDate fecha = LocalDate.parse(fechaAFormatearA[0]+fechaAFormatearA[1]+fechaAFormatearA[2],fechaFormatoEntrada);
            String[] bDatos = {title,fecha.toString(),link,cuerpo};
            datos.add(bDatos);

            logger.info("Añadiendo los datos al arreglo.");
          } else {
            logger.info("Se detectó una noticia exclusiva para miembros. Se saltea la noticia.");
          }
        }
        logger.info("Añadiendo todas las noticias obtenidas al archivo");
        csvWriter.writeAll(datos);

        logger.info("Datos guardados en {}", filePath);
      }
    } catch (DateTimeParseException date) {
      logger.error("No se pudo parsear la fecha para guardarlo en la base de datos. Exitting...");
    } catch (IOException e) {
      logger.error("Error al escribir/abrir en el archivo CSV");
    } catch (TimeoutException time) {
      logger.error("Se agotó el tiempo de espera del servidor.");
    } catch (StaleElementReferenceException e) {
      logger.error("El botón se movió de lugar o no se alcanzó a presionar el botón con el tiempo disponible para cargar el botón");
    } finally {
      // Cerrar el navegador
      driver.close();
      driver.quit();
    }
  }
}
