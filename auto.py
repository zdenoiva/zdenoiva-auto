import os
import zipfile
import tempfile
import urllib.request
import time
from selenium import webdriver
from selenium.webdriver.common.by import By
from selenium.webdriver.support.ui import WebDriverWait
from selenium.webdriver.support import expected_conditions as EC
from selenium.webdriver.chrome.options import Options
from selenium.webdriver.chrome.service import Service

# Načítanie prihlasovacích údajov zo Secrets
username_value = os.environ.get("USERNAME")
password_value = os.environ.get("PASSWORD")

def automatizacia():
    print("🤖 Spúšťam automatizáciu...")
    
    # 1. NASTAVENIE CHROME PREHLIADAČA
    options = Options()
    options.add_argument('--headless=new')
    options.add_argument('--no-sandbox')
    options.add_argument('--disable-dev-shm-usage')
    options.add_argument('--disable-gpu')
    options.add_argument('--window-size=1920,1080')
    options.add_argument(
        '--user-agent=Mozilla/5.0 (Windows NT 10.0; Win64; x64) '
        'AppleWebKit/537.36 (KHTML, like Gecko) '
        'Chrome/91.0.4472.124 Safari/537.36'
    )
    
    # 2. STIAHNUTIE A ROZBALENIE SPRÁVNEHO CHROMEDRIVERU
    tmp_dir = tempfile.mkdtemp()
    zip_path = os.path.join(tmp_dir, 'chromedriver.zip')

    # Získanie najnovšej Chromedriver verzie
    driver_version = urllib.request.urlopen(
        'https://chromedriver.storage.googleapis.com/LATEST_RELEASE'
    ).read().decode().strip()
    print(f"Using Chromedriver version: {driver_version}")

    url = f"https://chromedriver.storage.googleapis.com/{driver_version}/chromedriver_linux64.zip"
    urllib.request.urlretrieve(url, zip_path)
    
    with zipfile.ZipFile(zip_path, 'r') as archive:
        archive.extract('chromedriver', tmp_dir)
    driver_path = os.path.join(tmp_dir, 'chromedriver')
    os.chmod(driver_path, 0o755)
    
    service = Service(driver_path)
    
    # 3. SPUSTENIE PREHLIADAČA
    driver = webdriver.Chrome(service=service, options=options)
    
    try:
        # 4. PRIHLÁSENIE
        print("📂 Otváram login stránku...")
        driver.get("https://webbox.elko.sk/logindispatcher")
        WebDriverWait(driver, 15).until(
            EC.presence_of_element_located((By.ID, "username"))
        )
        
        print("🔐 Vyplňujem prihlasovacie údaje...")
        driver.find_element(By.ID, "username").send_keys(username_value)
        driver.find_element(By.ID, "password").send_keys(password_value)
        driver.find_element(By.XPATH, "//button[@type='submit']").click()
        
        print("🔄 Čakám na prihlásenie...")
        WebDriverWait(driver, 10).until(
            EC.presence_of_element_located((By.CLASS_NAME, "label"))
        )
        
        # 5. KONTROLA STAVU A AKCIA
        status = driver.find_element(By.CLASS_NAME, "label-danger").text.strip()
        print(f"📋 Stav: {status}")
        
        if status == "Práca / Príchod":
            btn = WebDriverWait(driver, 10).until(
                EC.element_to_be_clickable((
                    By.XPATH,
                    "//button[contains(@class,'rdr-make-transaction') and contains(text(),'Príchod')]"
                ))
            )
            btn.click()
            print("✅ Kliknuté na Príchod")
            time.sleep(2)
        
        # 6. ODHLÁSENIE
        driver.get("https://webbox.elko.sk/logout")
        print("✅ Odhlásenie dokončené")
        
    except Exception as e:
        print(f"❌ Chyba pri automatizácii: {e}")
        # Screenshot pre debugging
        try:
            driver.save_screenshot("chyba_screenshot.png")
            print("📸 Screenshot chyby uložený")
        except:
            pass
        # Uloženie HTML zdroja
        try:
            with open("page_source_error.html", "w", encoding="utf-8") as f:
                f.write(driver.page_source)
            print("📄 HTML zdroj uložený")
        except:
            pass
        raise
    
    finally:
        driver.quit()
        print("🔒 Prehliadač zatvorený")

if __name__ == "__main__":
    automatizacia()
