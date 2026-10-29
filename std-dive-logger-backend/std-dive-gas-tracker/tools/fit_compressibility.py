"""Fit Z(p) - 1 = A*p + B*p^2 + C*p^3 per gas to NIST isotherm data (DOMAIN section 4.2, task C2).

    python3 -I tools/fit_compressibility.py

Reads the committed tables src/test/resources/nist/<gas>-293K.tsv; a missing table is fetched from
NIST first (delete a table to refresh it).

Source: NIST Chemistry WebBook fluid properties, isotherm 293.15 K, 1-300 bar (rows above
300 bar are dropped: NIST returns one extra row at 301 bar). Z = p / (rho * R * T). Least squares
without intercept on Z - 1 over p, p^2, p^3 (p = absolute bar). Paste the printed constants
into VirialFit.java and bump MODEL_VERSION when they change.
"""

import datetime
import pathlib
import subprocess
import sys
import time

import numpy as np

R = 0.0831446261815324  # L*bar/(mol*K)
T = 293.15  # K
P_MAX = 300.0  # bar abs, upper end of the fit range
GASES = {"he": "C7440597", "o2": "C7782447", "n2": "C7727379"}
URL = (
    "https://webbook.nist.gov/cgi/fluid.cgi?Action=Data&Wide=on&ID={id}&Type=IsoTherm&Digits=6"
    "&PLow=1&PHigh=300&PInc=5&T=293.15&RefState=DEF&TUnit=K&PUnit=bar&DUnit=mol%2Fl"
    "&HUnit=kJ%2Fmol&WUnit=m%2Fs&VisUnit=uPa*s&STUnit=N%2Fm"
)
TABLE_DIR = pathlib.Path(__file__).resolve().parent.parent / "src/test/resources/nist"


def fetch(gas):
    """Download one isotherm and return trimmed rows (pressure bar, density mol/l, Z)."""
    url = URL.format(id=GASES[gas])
    # curl, not urllib: NIST answers Python's default client with 403. It also 504s now and then.
    for attempt in range(1, 7):
        done = subprocess.run(["curl", "-sS", "-f", "-m", "90", url], capture_output=True, text=True)
        if done.returncode == 0:
            text = done.stdout
            break
        print(f"{gas}: attempt {attempt} failed: {done.stderr.strip()}", file=sys.stderr)
        time.sleep(5)
    else:
        sys.exit(f"{gas}: NIST fetch failed")
    lines = text.splitlines()
    columns = lines[0].split("\t")
    i_t, i_p, i_rho = (columns.index(c) for c in ("Temperature (K)", "Pressure (bar)", "Density (mol/l)"))
    rows = {}
    for line in lines[1:]:
        cells = line.split("\t")
        if abs(float(cells[i_t]) - T) > 1e-9:
            sys.exit(f"{gas}: unexpected temperature {cells[i_t]}")
        pressure, density = float(cells[i_p]), float(cells[i_rho])
        if pressure > P_MAX:
            continue
        if pressure in rows and rows[pressure] != density:  # phase-boundary duplicates must agree
            sys.exit(f"{gas}: conflicting rows at {pressure} bar")
        rows[pressure] = density
    table = TABLE_DIR / f"{gas}-293K.tsv"
    TABLE_DIR.mkdir(parents=True, exist_ok=True)
    with open(table, "w", encoding="utf-8") as out:
        out.write(f"# {url} fetched {datetime.date.today()}; columns: pressure_bar, density_mol_per_l, z\n")
        for pressure, density in sorted(rows.items()):
            out.write(f"{pressure:g}\t{density:g}\t{pressure / (density * R * T):.7f}\n")


def read(gas):
    data = np.loadtxt(TABLE_DIR / f"{gas}-293K.tsv", comments="#", delimiter="\t")
    return data[:, 0], data[:, 2]


def main():
    for gas in GASES:
        if not (TABLE_DIR / f"{gas}-293K.tsv").exists():
            fetch(gas)
        p, z = read(gas)
        # Fit on p/P_MAX for conditioning, then scale back to coefficients of p, p^2, p^3.
        scaled = p / P_MAX
        design = np.column_stack([scaled, scaled**2, scaled**3])
        coeffs = np.linalg.lstsq(design, z - 1, rcond=None)[0] / [P_MAX, P_MAX**2, P_MAX**3]
        coeffs = np.array([float(f"{c:.6e}") for c in coeffs])  # exactly what goes into Java
        fitted = 1 + coeffs[0] * p + coeffs[1] * p**2 + coeffs[2] * p**3
        relative_error = np.abs(fitted - z) / z
        worst = int(np.argmax(relative_error))
        print(f"{gas.upper()}: {len(p)} rows, {p.min():g}-{p.max():g} bar")
        for name, value in zip("ABC", coeffs):
            print(f"  {gas.upper()}_{name} = {value:.6e}")
        print(f"  max relative error {relative_error[worst]:.4%} at {p[worst]:g} bar")


main()
