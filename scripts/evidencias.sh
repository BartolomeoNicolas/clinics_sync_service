#!/usr/bin/env bash
# Corre la suite y deja el resultado en un archivo, para poder volver a reproducir
# las evidencias del enunciado sin depender de la terminal.
#
#   ./scripts/evidencias.sh            # corre todo
#   ./scripts/evidencias.sh --rapido   # solo los tests, sin el informe
#
# El informe queda en build/evidencias/ con la salida de cada paso.

set -euo pipefail

cd "$(dirname "$0")/.."

RAIZ="$(pwd)"
SALIDA="$RAIZ/build/evidencias"
mkdir -p "$SALIDA"

JAVA_HOME="${JAVA_HOME:-$HOME/Facultad/android-studio/jbr}"
export JAVA_HOME

echo "==> Java"
java -version 2>&1 | tee "$SALIDA/00-java.txt"

echo "==> Suite completa"
./gradlew clean build 2>&1 | tee "$SALIDA/01-build.txt"

echo "==> Resultados por clase"
total=0
fallos=0
for archivo in build/test-results/test/TEST-*.xml; do
  [ -e "$archivo" ] || continue
  clase="$(basename "$archivo" .xml | sed 's/^TEST-//')"
  pruebas="$(grep -o 'tests="[0-9]*"' "$archivo" | head -1 | grep -o '[0-9]*')"
  fallas="$(grep -o 'failures="[0-9]*"' "$archivo" | head -1 | grep -o '[0-9]*')"
  errores="$(grep -o 'errors="[0-9]*"' "$archivo" | head -1 | grep -o '[0-9]*')"
  total=$((total + pruebas))
  fallos=$((fallos + fallas + errores))
  printf '%-72s %4s pruebas  %s fallos\n' "$clase" "$pruebas" "$((fallas + errores))" \
    | tee -a "$SALIDA/02-resultados.txt"
done

{
  echo ""
  echo "Total: $total pruebas, $fallos fallos"
} | tee -a "$SALIDA/02-resultados.txt"

echo
echo "Informe en $SALIDA"
