# 11 · Una sola firma de depuración para todas las máquinas

> **Estado:** configurado (clave en `app/debug.keystore`, bloque `signingConfigs` en `app/build.gradle.kts`). La clave se generó y se comprobó con `keytool`; **no se ha comprobado una instalación real** con ella (en este entorno no hay Android SDK). Tras el primer `installDebug` con esta clave hay que **desinstalar una última vez** (sección 7).

## Índice

1. [El problema](#1-el-problema)
2. [Teoría: qué es firmar un APK y por qué Android se niega](#2-teoría-qué-es-firmar-un-apk-y-por-qué-android-se-niega)
3. [Por qué cada máquina tiene su clave de depuración](#3-por-qué-cada-máquina-tiene-su-clave-de-depuración)
4. [Las opciones](#4-las-opciones)
5. [Lo hecho: la clave en el repositorio](#5-lo-hecho-la-clave-en-el-repositorio)
6. [Seguridad: por qué esto es aceptable](#6-seguridad-por-qué-esto-es-aceptable)
7. [La migración: desinstalar una última vez](#7-la-migración-desinstalar-una-última-vez)
8. [Cómo comprobarlo](#8-cómo-comprobarlo)
9. [Trampas](#9-trampas)
10. [Decisión](#10-decisión)
11. [Qué recordar](#11-qué-recordar)

---

## 1. El problema

Al instalar desde la terminal (`./gradlew :app:installDebug`) sobre una versión instalada antes **desde otra máquina**:

```
INSTALL_FAILED_UPDATE_INCOMPATIBLE: Existing package dev.knowledge82.swiftyproteins
signatures do not match newer version; ignoring!
```

La compilación y las pruebas iban bien: el fallo es **solo de instalación**, y no tiene nada que ver con el código.

---

## 2. Teoría: qué es firmar un APK y por qué Android se niega

- **Todo APK va firmado.** La firma es una huella criptográfica hecha con una **clave privada**; el APK lleva el **certificado** (la parte pública) para comprobarla. Android **no instala** un APK sin firma.
- **La firma identifica al autor.** Para Android, «la misma app» = **mismo nombre de paquete + mismo certificado**.
- **Una actualización debe venir del mismo autor.** Si el paquete ya está instalado y el APK nuevo trae **otro** certificado, Android **lo rechaza**. Es una protección de seguridad: si no, cualquiera podría publicar un APK con tu mismo nombre de paquete, «actualizar» tu app y heredar **sus datos** (contraseñas, tokens…).
- **La única salida** con otra firma es **desinstalar** (y con ello se borran los datos de la app) e instalar de nuevo.

---

## 3. Por qué cada máquina tiene su clave de depuración

Las compilaciones **debug** se firman solas, sin que nadie configure nada, con una clave de depuración que el SDK de Android guarda en **`~/.android/debug.keystore`**. Si el fichero no existe, el SDK **lo crea** en la primera compilación, con una clave **nueva y aleatoria**.

Consecuencia: **cada máquina (y cada usuario) tiene su propia clave**. En nuestro caso:

| Dónde | `~/.android/debug.keystore` |
|-------|------------------------------|
| Ordenadores del campus | La carpeta personal es **la misma** en todos → una clave para todo el campus |
| Portátil de casa | Otra clave |
| Portátil del compañero | Otra más |

Instalar en el A33 desde casa algo que antes se instaló desde el campus (o al revés) → firmas distintas → el error de la sección 1. También pasa si alguien **borra `~/.android`**: el SDK crea una clave nueva sin avisar.

---

## 4. Las opciones

| Opción | Cómo | Pros | Contras |
|--------|------|------|---------|
| 1. Desinstalar cada vez | `adb uninstall dev.knowledge82.swiftyproteins` | Nada que configurar | Pesado; **borra los datos** (tras la etapa de acceso: la cuenta creada) |
| 2. Copiar `~/.android/debug.keystore` a mano a todas las máquinas | `scp` o un pendrive | No toca el proyecto | Hay que acordarse en cada máquina nueva; el compañero también |
| **3. La clave en el repositorio** (elegida) | `app/debug.keystore` + `signingConfigs` | **Cualquier** máquina que clone el proyecto firma igual; cero pasos manuales | La clave está en git (sección 6) |

---

## 5. Lo hecho: la clave en el repositorio

**La clave** se generó con `keytool` (viene con cualquier JDK) con **los mismos valores estándar** que usa el SDK para las claves de depuración:

```bash
keytool -genkeypair -keystore app/debug.keystore -storetype PKCS12 \
    -storepass android -keypass android -alias androiddebugkey \
    -keyalg RSA -keysize 2048 -validity 10000 -dname "CN=Android Debug,O=Android,C=US"
```

- **PKCS12**: el formato estándar de almacén de claves desde Java 9 (el antiguo era JKS). En PKCS12 la contraseña del almacén y la de la clave deben **coincidir** (`android` / `android`).
- **RSA 2048, 10 000 días** (válida hasta 2054): valores habituales de depuración.
- Comprobada con `keytool -list -v`: alias `androiddebugkey`, propietario `CN=Android Debug, O=Android, C=US`, huella SHA-256 `56:12:61:95:…:74:48:B2`.

**La configuración** (`app/build.gradle.kts`):

```kotlin
signingConfigs {
    getByName("debug") {
        storeFile = file("debug.keystore")
        storePassword = "android"
        keyAlias = "androiddebugkey"
        keyPassword = "android"
    }
}
```

- `signingConfigs` ya trae una configuración llamada **`debug`** (la que apunta a `~/.android/debug.keystore`); `getByName("debug")` la **modifica** en vez de crear otra.
- `file("debug.keystore")` se resuelve **respecto a la carpeta del módulo** (`app/`).
- El tipo de compilación `debug` usa la configuración `debug` **por defecto**: no hace falta tocar `buildTypes`.
- La compilación **release** no se ve afectada: no tiene configuración de firma (no se publica).

---

## 6. Seguridad: por qué esto es aceptable

- **Una clave de depuración no protege nada que importe:** su contraseña es **pública** (`android` en todo el mundo) y solo firma compilaciones de prueba.
- Lo que **nunca** debe ir a un repositorio es una **clave de publicación** (*release*): con ella, cualquiera podría firmar actualizaciones «oficiales» de la app. Si algún día hiciera falta, iría **fuera** del repositorio (variables de entorno o un `keystore.properties` ignorado por git).
- El riesgo que queda: alguien con el repositorio podría firmar un APK que **actualice** la versión de depuración instalada en vuestros móviles. Para un proyecto escolar que no se publica, es aceptable; **es lo habitual en equipos**.

---

## 7. La migración: desinstalar una última vez

La app que hay ahora en el A33 está firmada con la clave de **alguna** máquina; la nueva compilación se firma con la clave del repositorio, **distinta** de todas. Así que **una vez más**:

```bash
adb uninstall dev.knowledge82.swiftyproteins     # o: ./gradlew :app:uninstallDebug
./gradlew :app:installDebug
```

A partir de ahí, `installDebug` funciona **desde cualquier máquina** sin desinstalar. El compañero, en su móvil, igual: una desinstalación y listo.

---

## 8. Cómo comprobarlo

```bash
# La huella del certificado con el que se firmó el APK compilado:
keytool -printcert -jarfile app/build/outputs/apk/debug/app-debug.apk | grep SHA256
# Debe coincidir con la de la clave del repositorio:
keytool -list -v -keystore app/debug.keystore -storepass android | grep SHA256
```

Con el SDK instalado también vale `apksigner verify --print-certs app/build/outputs/apk/debug/app-debug.apk` (está en `build-tools/<versión>/`). La prueba real: compilar e instalar desde **dos máquinas distintas** seguidas, sin desinstalar.

---

## 9. Trampas

1. **Cambiar la clave más adelante** (regenerarla, otro alias) vuelve a provocar el error: una desinstalación más.
2. **No confundir con la clave de publicación:** este fichero no debe usarse jamás para firmar un *release*.
3. **`.gitignore`:** si alguien añade una regla genérica `*.keystore`, la clave dejaría de subirse y cada máquina volvería a su propia clave sin avisar. Hoy no hay tal regla (comprobado).
4. **Contraseñas en el script de Gradle:** aceptables **solo** porque son las públicas de depuración.

---

## 10. Decisión

| # | Decisión | Estado | Motivo |
|---|----------|--------|--------|
| D37 | Las compilaciones debug se firman con **una clave de depuración común guardada en el repositorio** (`app/debug.keystore`, valores estándar) | Decidida | Instalar desde cualquier máquina sin desinstalar ni perder datos; práctica habitual en equipos; la clave de depuración no protege nada |

---

## 11. Qué recordar

- **Mismo paquete + otra firma = Android rechaza la actualización.** Es seguridad, no un fallo.
- Las firmas de depuración salen de `~/.android/debug.keystore`, **una por máquina** (se crea sola).
- **Solución de equipo:** una clave de depuración en el repositorio + `signingConfigs.getByName("debug")`.
- **Nunca** una clave de publicación en git.
- **Una última desinstalación** al pasar a la clave común.
