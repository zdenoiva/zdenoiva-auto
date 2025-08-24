from selenium import webdriver
from selenium.webdriver.chrome.options import Options
from selenium.webdriver.chrome.service import Service
from webdriver_manager.chrome import ChromeDriverManager
from selenium.webdriver.common.by import By
from selenium.webdriver.support.ui import WebDriverWait
from selenium.webdriver.support import expected_conditions as EC
import time
import os

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
    options.add_argument('--disable-web-security')
    options.add_argument('--window-size=1920,1080')
    options.add_argument(
        '--user-agent=Mozilla/5.0 (Windows NT 10.0; Win64; x64) '
        'AppleWebKit/537.36 (KHTML, like Gecko) '
        'Chrome/91.0.4472.124 Safari/537.36'
    )
    
        # 2. Spustenie prehliadača (systémový chromedriver)
    from webdriver_manager.core.utils import ChromeType
    from webdriver_manager.chrome import ChromeDriverManager

        # Získajte cestu k driveru vrátane koncového 'chromedriver'
    driver_path = ChromeDriverManager(chrome_type=ChromeType.GOOGLE).install()
    print(f"Driver path: {driver_path}")
    service = Service(driver_path)

    driver = webdriver.Chrome(service=service, options=options)
    
    try:
        # ===========================================
        # 4. PRIHLÁSENIE
        # ===========================================
        print("📂 Otváram login stránku...")
        driver.get("https://webbox.elko.sk/logindispatcher")
        
        WebDriverWait(driver, 15).until(
            EC.presence_of_element_located((By.ID, "username"))
        )
        print(f"✅ Login stránka načítaná: {driver.title}")
        
        print("🔐 Vyplňujem prihlasovacie údaje...")
        username_field = driver.find_element(By.ID, "username")
        username_field.clear()
        username_field.send_keys(username_value)
        print("✅ Používateľské meno vyplnené")
        
        password_field = driver.find_element(By.ID, "password")
        password_field.clear()
        password_field.send_keys(password_value)
        print("✅ Heslo vyplnené")
        
        try:
            login_button = driver.find_element(By.XPATH, "//button[@type='submit']")
        except:
            try:
                login_button = driver.find_element(By.XPATH, "//input[@type='submit']")
            except:
                form = driver.find_element(By.TAG_NAME, "form")
                form.submit()
                login_button = None
        
        if login_button:
            login_button.click()
        
        print("🔄 Kliknuté na prihlásenie, čakám na načítanie...")
        time.sleep(5)
        
        try:
            WebDriverWait(driver, 10).until(
                EC.presence_of_element_located((By.CLASS_NAME, "label"))
            )
            print("✅ Prihlásenie úspešné!")
        except:
            print("❌ Prihlásenie možno neúspešné, pokračujem...")
        
        driver.save_screenshot("po_prihlaseni.png")
        print("📸 Screenshot po prihlásení uložený")
        
        # ===========================================
        # 5. KONTROLA STAVU A ROZHODOVANIE
        # ===========================================
        print("🔍 Kontrolujem aktuálny stav...")
        
        try:
            status_element = driver.find_element(By.CLASS_NAME, "label-danger")
            current_status = status_element.text.strip()
            print(f"📋 Aktuálny stav: '{current_status}'")
            
            if current_status == "Práca / Odchod":
                print("🏠 Stav je 'Práca / Odchod' - nič nerobím, len sa odhlásim")
                action_taken = "Žiadna akcia - už odchodový stav"
                
            elif current_status == "Práca / Príchod":
                print("🏢 Stav je 'Práca / Príchod' - klikám na tlačidlo Príchod")
                prichod_button = WebDriverWait(driver, 10).until(
                    EC.element_to_be_clickable((
                        By.XPATH,
                        "//button[contains(@class,'rdr-make-transaction') "
                        "and contains(text(),'Príchod')]"
                    ))
                )
                prichod_button.click()
                print("✅ Tlačidlo 'Príchod' stlačené!")
                time.sleep(3)
                driver.save_screenshot("po_akcii.png")
                print("📸 Screenshot po akcii uložený")
                action_taken = "Kliknuté na tlačidlo Príchod"
                
            else:
                print(f"⚠️ Neznámy stav: '{current_status}' - nič nerobím")
                action_taken = f"Neznámy stav: {current_status}"
                
        except Exception as e:
            print(f"❌ Chyba pri kontrole stavu: {e}")
            try:
                all_labels = driver.find_elements(By.CLASS_NAME, "label")
                print(f"🔍 Nájdené labels: {[lbl.text for lbl in all_labels]}")
            except:
                pass
            action_taken = f"Chyba pri kontrole stavu: {e}"
        
        # ===========================================
        # 6. ODHLÁSENIE
        # ===========================================
        print("🚪 Odhlasujem sa...")
        driver.get("https://webbox.elko.sk/logout")
        time.sleep(2)
        print("✅ Odhlásenie dokončené!")
        print(f"🎯 Celková akcia: {action_taken}")
        print("🎉 Automatizácia úspešne dokončená!")
        return action_taken
        
    except Exception as e:
        print(f"❌ Chyba pri automatizácii: {e}")
        try:
            driver.save_screenshot("chyba_screenshot.png")
            print("📸 Screenshot chyby uložený")
        except:
            pass
        try:
            with open("page_source_error.html", "w", encoding="utf-8") as f:
                f.write(driver.page_source)
            print("📄 HTML zdroj uložený do page_source_error.html")
        except:
            pass
        raise e
        
    finally:
        driver.quit()
        print("🔒 Prehliadač zatvorený")

if __name__ == "__main__":
    try:
        result = automatizacia()
        print(f"\n✅ FINÁLNY VÝSLEDOK: {result}")
    except Exception as e:
        print(f"\n❌ FINÁLNA CHYBA: {e}")
        exit(1)
