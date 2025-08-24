import os
import time
import chromedriver_autoinstaller
from selenium import webdriver
from selenium.common.exceptions import UnexpectedAlertPresentException, NoAlertPresentException
from selenium.webdriver.common.by import By
from selenium.webdriver.support.ui import WebDriverWait
from selenium.webdriver.support import expected_conditions as EC
from selenium.webdriver.chrome.options import Options

# Load credentials
username_value = os.environ["USERNAME"]
password_value = os.environ["PASSWORD"]

def automatizacia():
    print("🤖 Spúšťam automatizáciu...")

    # 1. ChromeOptions with media/camera disabled
    options = Options()
    options.add_argument('--headless=new')
    options.add_argument('--no-sandbox')
    options.add_argument('--disable-dev-shm-usage')
    options.add_argument('--disable-gpu')
    options.add_argument('--window-size=1920,1080')
    # Prevent camera/mic errors
    options.add_argument('--use-fake-ui-for-media-stream')
    options.add_argument('--use-fake-device-for-media-stream')
    options.add_argument('--disable-media-stream')

    # 2. Auto-install matching ChromeDriver
    chromedriver_autoinstaller.install()

    # 3. Launch browser
    driver = webdriver.Chrome(options=options)

    def safe_wait(locator, timeout=15):
        """Wait and dismiss any unexpected alert before retrying."""
        end = time.time() + timeout
        while True:
            try:
                return WebDriverWait(driver, 1).until(EC.presence_of_element_located(locator))
            except UnexpectedAlertPresentException:
                try:
                    alert = driver.switch_to.alert
                    print(f"⚠️ Dismissing alert: {alert.text}")
                    alert.dismiss()
                except NoAlertPresentException:
                    pass
            except Exception:
                if time.time() > end:
                    raise

    try:
        # 4. LOGIN
        print("📂 Otváram login stránku...")
        driver.get("https://webbox.elko.sk/logindispatcher")
        safe_wait((By.ID, "username"))
        driver.find_element(By.ID, "username").send_keys(username_value)
        driver.find_element(By.ID, "password").send_keys(password_value)
        driver.find_element(By.XPATH, "//button[@type='submit']").click()

        # 5. WAIT FOR DASHBOARD ELEMENT
        print("🔄 Čakám na dashboard...")
        safe_wait((By.CLASS_NAME, "label-success"))

        # 6. CHECK STATUS AND ACT
        status = driver.find_element(By.CLASS_NAME, "label-success").text.strip()
        print(f"📋 Aktuálny stav: {status}")
        if status == "Práca / Príchod":
            selector = "button.rdr-make-transaction[data-label='Práca / Odchod']"
            btn = WebDriverWait(driver, 10).until(
            EC.element_to_be_clickable((By.CSS_SELECTOR, selector))
            )
            btn.click()
            print("✅ Kliknuté na Odchod")

            # Čakáme na potvrďovací dialóg
            WebDriverWait(driver, 10).until(
                EC.element_to_be_clickable((By.ID, "rdr-confirm-btn"))
            )
            # Klikneme na potvrdiť a odhlásiť
            driver.find_element(By.ID, "rdr-confirm-btn").click()
            print("✅ Potvrdený odchod")

        # 7. LOGOUT
        driver.get("https://webbox.elko.sk/logout")
        print("✅ Odhlásenie dokončené")
        driver.save_screenshot("po_odhlaseni.png")

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
            print("📄 HTML zdroj uložený")
        except:
            pass
        raise

    finally:
        driver.quit()
        print("🔒 Prehliadač zatvorený")

if __name__ == "__main__":
    automatizacia()
