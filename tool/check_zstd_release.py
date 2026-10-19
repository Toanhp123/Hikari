"""Check JNI retention in a shrunk release APK, without test references keeping classes.

python -I tool/check_zstd_release.py APK --apkanalyzer PATH_TO_SDK_APKANALYZER
"""

import argparse
import subprocess


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("apk", help="Shrunk release APK, not a debug/instrumentation APK")
    parser.add_argument("--apkanalyzer", default="apkanalyzer", help="Android SDK apkanalyzer executable")
    args = parser.parse_args()
    result = subprocess.run(
        [args.apkanalyzer, "dex", "packages", "--defined-only", args.apk],
        check=True, capture_output=True, text=True,
    )
    # SDK output: kind, defined/referenced, method counts, size, qualified definition.
    definitions = set()
    for line in result.stdout.splitlines():
        columns = line.split("\t", 3)
        if len(columns) == 4:
            definitions.add((columns[0].split()[0], columns[3].strip()))
    required = {
        "ZstdCompressor": ["int inputBytesProcessed", "int outputBytesProcessed"],
        "ZstdDecompressor": ["int inputBytesProcessed", "int outputBytesProcessed"],
        "JniZstdKt": ["long createJniZstd()", "java.lang.String jniGetErrorName(long)",
                      "long createZstdCompressor()", "long createZstdDecompressor()"],
        "JniZstdCompressor": ["<init>()", "long setParameter(long,int,int)",
                              "long compressStream2(long,long,byte[],int,int,byte[],int,int,int)",
                              "void close(long)"],
        "JniZstdDecompressor": ["<init>()",
                                "long decompressStream(long,long,byte[],int,int,byte[],int,int)",
                                "void close(long)"],
    }
    failures = []
    for name, members in required.items():
        name = "com.squareup.zstd." + name
        if ("C", name) not in definitions:
            failures.append("Missing class definition: " + name)
            continue
        for member in members:
            kind = "M" if "(" in member else "F"
            if (kind, name + " " + member) not in definitions:
                failures.append(f"Missing declared member: {name} {member}")
    if failures:
        parser.exit(1, "Zstd JNI retention FAILED:\n" + "\n".join(failures) + "\n")
    print("Zstd JNI retention OK: class definitions, JNI fields and signatures retained")


if __name__ == "__main__":
    main()
