/* Second attempt */
bool wantp = false, wantq = false;
#define notmutex (p@csp && q@csq)

active proctype p() {
    do :: !wantq;
          wantp = true;
csp:      wantp = false;
    od
}

active proctype q() {
    do :: !wantp;
          wantq = true;
csq:      wantq = false;
    od
}

