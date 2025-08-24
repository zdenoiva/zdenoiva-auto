from selenium import webdriver
from selenium.webdriver.common.by import By
from selenium.webdriver.support.ui import WebDriverWait
from selenium.webdriver.support import expected_conditions as EC
from selenium.webdriver.chrome.options import Options
from selenium.webdriver.chrome.service import Service
from webdriver_manager.chrome import ChromeDriverManager
import time
import os

def automatizacia():
    print("🤖 Spúšťam automatizáciu...")
    
    # NASTAVENIE CHROME PREHLIADAČA
    options = Options()
    # Pre GitHub Actions (headless mode - bez okna)
    options.add_argument('--headless=new')
    options.add_argument('--no-sandbox')
    options.add_argument('--disable-dev-shm-usage')
    options.add_argument('--disable-gpu')
    options.add_argument('--disable-web-security')
    options.add_argument('--window-size=1920,1080')
    options.add_argument('--user-agent=Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36')
    
    # Automatická inštalácia ChromeDriver
    service = Service(ChromeDriverManager().install())
    
    # Spustenie prehliadača
    driver = webdriver.Chrome(service=service, options=options)
    
    try:
        # ===========================================
        # 1. PRIHLÁSENIE
        # ===========================================
        print("📂 Otváram login stránku...")
        driver.get("https://webbox.elko.sk/logindispatcher")
        
        # Počkaj kým sa stránka načíta
        WebDriverWait(driver, 15).until(
            EC.presence_of_element_located((By.ID, "username"))
        )
        print(f"✅ Login stránka načítaná: {driver.title}")
        
        # ===========================================
        # 2. VYPLNENIE PRIHLASOVACÍCH ÚDAJOV
        # ===========================================
        print("🔐 Vyplňujem prihlasovacie údaje...")
        
        # Nájdi a vyplň pole pre používateľské meno
        username_field = driver.find_element(By.ID, "username")
        username_field.clear()
        username_field.send_keys("REDACTED")  # Vaše používateľské meno
        print("✅ Používateľské meno vyplnené")
        
        # Nájdi a vyplň pole pre heslo
        password_field = driver.find_element(By.ID, "password")
        password_field.clear()
        password_field.send_keys("REDACTED")  # Vaše heslo
        print("✅ Heslo vyplnené")
        
        # Nájdi a klikni na tlačidlo prihlásenia
        # Hľadáme submit tlačidlo alebo form submit
        try:
            # Pokus o nájdenie submit tlačidla
            login_button = driver.find_element(By.XPATH, "//button[@type='submit']")
        except:
            try:
                # Alternatívne hľadanie
                login_button = driver.find_element(By.XPATH, "//input[@type='submit']")
            except:
                # Posledná možnosť - form submit
                form = driver.find_element(By.TAG_NAME, "form")
                form.submit()
                login_button = None
        
        if login_button:
            login_button.click()
        
        print("🔄 Kliknuté na prihlásenie, čakám na načítanie...")
        
        # ===========================================
        # 3. OVERENIE ÚSPEŠNÉHO PRIHLÁSENIA
        # ===========================================
        time.sleep(5)  # Počkaj na načítanie stránky
        
        # Skontroluj či sme úspešne prihlásení (hľadaj dashboard alebo určitý element)
        try:
            WebDriverWait(driver, 10).until(
                EC.presence_of_element_located((By.CLASS_NAME, "label"))
            )
            print("✅ Prihlásenie úspešné!")
        except:
            print("❌ Prihlásenie možno neúspešné, pokračujem...")
        
        # Screenshot pre debugging
        driver.save_screenshot("po_prihlaseni.png")
        print("📸 Screenshot po prihlásení uložený")
        
        # ===========================================
        # 4. KONTROLA STAVU A ROZHODOVANIE
        # ===========================================
        print("🔍 Kontrolujem aktuálny stav...")
        
        try:
            # Nájdi span element s label-danger class
            status_element = driver.find_element(By.CLASS_NAME, "label-danger")
            current_status = status_element.text.strip()
            print(f"📋 Aktuálny stav: '{current_status}'")
            
            if current_status == "Práca / Odchod":
                print("🏠 Stav je 'Práca / Odchod' - nic nerobím, len sa odhlásim")
                action_taken = "Žiadna akcia - už odchodový stav"
                
            elif current_status == "Práca / Príchod":
                print("🏢 Stav je 'Práca / Príchod' - klikám na tlačidlo Odchod")
                
                # Nájdi a klikni na tlačidlo Odchod
                prichod_button = WebDriverWait(driver, 10).until(
                    EC.element_to_be_clickable((By.XPATH, "//button[contains(@class, 'rdr-make-transaction') and contains(text(), 'Odchod')]"))
                )
                prichod_button.click()
                print("✅ Tlačidlo 'Odchod' stlačené!")
                
                # Počkaj na spracovanie
                time.sleep(3)
                action_taken = "Kliknuté na tlačidlo Odchod"
                
                # Screenshot po akcii
                driver.save_screenshot("po_akcii.png")
                print("📸 Screenshot po akcii uložený")
                
            else:
                print(f"⚠️ Neznámy stav: '{current_status}' - nic nerobím")
                action_taken = f"Neznámy stav: {current_status}"
                
        except Exception as e:
            print(f"❌ Chyba pri kontrole stavu: {e}")
            # Pokús sa nájsť iné elementy pre debugging
            try:
                all_labels = driver.find_elements(By.CLASS_NAME, "label")
                print(f"🔍 Nájdené labels: {[label.text for label in all_labels]}")
            except:
                pass
            action_taken = f"Chyba pri kontrole stavu: {e}"
        
        # ===========================================
        # 5. ODHLÁSENIE
        # ===========================================
        print("🚪 Odhlasujem sa...")
        
        # Choď na logout URL
        driver.get("https://webbox.elko.sk/logout")
        time.sleep(2)
        
        print("✅ Odhlásenie dokončené!")
        print(f"🎯 Celková akcia: {action_taken}")
        print("🎉 Automatizácia úspešne dokončená!")
        
        return action_taken
        
    except Exception as e:
        print(f"❌ Chyba pri automatizácii: {e}")
        # Urobí screenshot pre debugging
        try:
            driver.save_screenshot("chyba_screenshot.png")
            print("📸 Screenshot chyby uložený")
        except:
            pass
        
        # Výpis HTML zdroja pre debugging
        try:
            with open("page_source_error.html", "w", encoding="utf-8") as f:
                f.write(driver.page_source)
            print("📄 HTML zdroj uložený do page_source_error.html")
        except:
            pass
        
        raise e  # Pre GitHub Actions - ukáže chybu
        
    finally:
        # Vždy zatvorí prehliadač
        driver.quit()
        print("🔒 Prehliadač zatvorený")

if __name__ == "__main__":
    try:
        result = automatizacia()
        print(f"\n✅ FINÁLNY VÝSLEDOK: {result}")
    except Exception as e:
        print(f"\n❌ FINÁLNA CHYBA: {e}")
        exit(1)  # Exit s chybou pre GitHub Actions
