package com.example.roman.testapp.jweb;
 
import org.jsoup.nodes.Element;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.Date;

/*
  <li class="clr" id="podcast-item-18077">
    <a onclick="playMedia(&#39;18077&#39;,
                          &#39;true&#39;,
                          &#39;Páteční houseparty&#39;,
                          &#39;To nejlepší z Ranní show&#39;,
                          &#39;100&#39;,
                          &#39;/img/edee/tym-a-porady/mp3-archiv/leos_patrik-1.jpg&#39;,
                          &#39;/file/edee/tym-a-porady/mp3-archiv/18077/zvuky-patecni-houseparty.mp3&#39;,
                          &#39;/mp3-archiv/patecni-houseparty-18077&#39;,
                          &#39;27. 2. 2015&#39;); 
                return false;" 
       href="http://www.evropa2.cz/mp3-archiv/patecni-houseparty-18077">
      Páteční houseparty<br>To nejlepší z Ranní show (27. 2. 2015)
    </a>
    <p title="74" class="rating" id="rating-18077">
      <img width="74%" height="15" alt="" src="./Páteční houseparty · MP3 archiv To nejlepší z Ranní show · Evropa 2 · MaXXimum muziky_files/spacer.gif">
    </p>
  </li>
*/

/**
 * Vytvori zaznam z html kodu.
 * 
 * @author Roman Zelenik
 */
public class RecordParser extends Parser{

  private static final int RECORD_ID = 0,
                             RECORD_NAME = 2,
                             RECORD_MP3 = 6,
                             RECORD_SITE = 7,
                             DATE = 8;

  public Record parse(Element element) throws IndexOutOfBoundsException, MalformedURLException, java.text.ParseException {
    Date date;
    URL mp3 = null;
    String dateStr = "",
           name,
           mp3Str = null;
    int id;

    String[] jsParams = parsePlayFun(element);
    name = jsParams[RECORD_NAME].trim();
    id = Integer.parseInt(jsParams[RECORD_ID].trim());
    mp3Str = jsParams[RECORD_MP3].trim();
    mp3 = new URL(mp3Str);
    dateStr = jsParams[DATE].trim();
    date = Record.dateFormat.parse(dateStr);
    return new Record(id, name, mp3, date);
  }

}
