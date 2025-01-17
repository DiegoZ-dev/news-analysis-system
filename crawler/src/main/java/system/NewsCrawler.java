package system;

import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Vector;
import java.util.Iterator;
import java.util.List;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.opencsv.CSVReader;
import com.opencsv.CSVWriter;

import com.microsoft.playwright.*;

public class NewsCrawler {
  private static final Logger logger = LoggerFactory.getLogger(NewsCrawler.class);

  public static void main(String[] args) {
    String url = "https://www.lanacion.com.ar/tema/videojuegos-tid48572/";
    String csvFolder = "data/raw";
    String csvFile = "news.csv";
    List<String[]> datosAlmacenados = new Vector<String[]>(1000, 1000);
    boolean existsFile = false;
    int cantNoticiasIniciales = 0;
    int cantNoticiasFinales = 0;
    Playwright playwright = Playwright.create();
    Page pagina;

    try {
      Path directory = Paths.get(csvFolder).toAbsolutePath().normalize();
      if (!Files.exists(directory)) {
        Files.createDirectories(directory);
        logger.info("Directorio creado en: {}", directory);
      } else {
        logger.info("El directorio ya existe en: {}", directory);
      }

      Path filePath = Paths.get(csvFolder, csvFile).toAbsolutePath().normalize();
      if (!Files.exists(filePath)) {
        logger.info("El archivo se guardará en: {}", filePath);
      } else {
        CSVReader leer = new CSVReader(new FileReader(filePath.toString()));
        datosAlmacenados = leer.readAll();
        datosAlmacenados.removeFirst();
        cantNoticiasIniciales = datosAlmacenados.size();
        leer.close();
        existsFile = true;
        logger.info("El archivo ya existe. Se almacenan los datos en un arreglo.");
      }

      // Preparar el archivo CSV para escritura
      try (Browser navegador = playwright.chromium().launch(new BrowserType.LaunchOptions().setHeadless(true))) {
        CSVWriter csvWriter = new CSVWriter(new FileWriter(filePath.toString()));
        pagina = navegador.newPage();
        pagina.navigate(url);
        List<String[]> datosNuevos = new Vector<String[]>(1000, 1000);

        String[] headers = { "titulo", "fecha", "link", "cuerpo" };
        csvWriter.writeNext(headers);
        logger.info("Añadiendo los headers: '{}' al archivo {}", headers, csvFile);

        int maxClicks = 1000;
        int clicks = 0;
        boolean exist = true;
        Locator masNoticias = pagina.locator("button.com-button.--secondary");
        while(masNoticias.isVisible()){
          masNoticias.click();
          logger.info("Haciendo clic en el botón 'Cargar más'. Click número: {}", clicks + 1);
          pagina.waitForTimeout(1000);
          clicks++;
        }
        logger.info("No se encontró el botón 'Cargar más'. Finalizando la carga de resultados.");

        Document doc = Jsoup.parse(pagina.content());
        Elements articlesGames = doc.select("div.row-gap-tablet-3 section.mod-description");

        int newsCount = 0;
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("d-MMMM-yyyy");
        Iterator<Element> articles = articlesGames.iterator();
        boolean parar = false;
        while (articles.hasNext() && !parar) {
            Element article = articles.next();
            if (article.select("span.badge.--sixxs.--arial.com-label.--exclusive-ln").isEmpty()) {
                Elements txtLink = article.select("h2.com-title.--font-primary.--l.--font-medium a.com-link");
                String link = "https://www.lanacion.com.ar" + txtLink.attr("href");

                logger.info("Noticia #{} - Entrando a la noticia del link {}", ++newsCount, link);
                Document news = Jsoup.connect(link).get();

                String title = news.select("h1.com-title.--font-primary.--sixxl.--font-extra").text();
                String fechaAFormatear = news.select("time.com-date").attr("datetime");
                String cuerpo = news.select("p.com-paragraph.--s").text();
                String[] fechaAFormatearA = fechaAFormatear.split(" de ");

                if (fechaAFormatearA.length == 3) {
                    String fechaFormateada = fechaAFormatearA[0] + "-" + fechaAFormatearA[1] + "-" + fechaAFormatearA[2];

                    try {
                        LocalDate fecha = LocalDate.parse(fechaFormateada, formatter);
                        String[] nuevaNoticia = { title, fecha.toString(), link, cuerpo };

                        // Comparar con todas las noticias almacenadas
                        boolean noticiaDuplicada = datosAlmacenados.stream().anyMatch(noticia -> igual(noticia, nuevaNoticia));

                        if (!noticiaDuplicada) {
                            // Añadir la noticia a la lista de nuevas noticias
                            datosNuevos.add(nuevaNoticia);
                        } else {
                            logger.info("Noticia duplicada, se ya se obtuvieron todas las noticias nuevas, deteniendo el scraping.");
                            parar = true;  // Detener al encontrar una noticia ya existente
                        }

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

        // Guardar las noticias recolectadas
        logger.info("Añadiendo todas las noticias obtenidas al archivo");

        if (existsFile) {
            // Insertar nuevas noticias al principio de los datos almacenados
            datosAlmacenados.addAll(0, datosNuevos);  // Añadir al inicio de la lista de noticias almacenadas
            csvWriter.writeAll(datosAlmacenados);     // Guardar todo en el archivo
            cantNoticiasFinales = datosAlmacenados.size();
        } else {
            csvWriter.writeAll(datosNuevos);  // Si no existe archivo previo, escribir solo las nuevas noticias
            cantNoticiasFinales = datosNuevos.size();
        }

        logger.info("Cantidad de noticias al inicio: {}", cantNoticiasIniciales);
        logger.info("Cantidad de noticias después del crawling: {}", cantNoticiasFinales);

        logger.info("Datos guardados en {}", filePath);
      }
    } catch (IOException e) {
      logger.error("Error al escribir/abrir en el archivo CSV", e);
    } catch (Exception e) {
      logger.error("Error durante la ejecución: ", e);
    }
  }

  private static boolean igual(String[] original, String[] comparar) {
    boolean sonIguales = false;
    if (original.length == comparar.length) {
      sonIguales = true;
      for (int i = 0; i < original.length && sonIguales; i++) {
        if (!original[i].equalsIgnoreCase(comparar[i]))
          sonIguales = false;
      }
    }
    return sonIguales;
  }
}
