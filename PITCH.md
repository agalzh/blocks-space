# Stack Unknown — 90-second demo

Target: ~225 spoken words + 5 chat commands in 90s. Practice once; do not improvise.

---

## Cold open (0:00–0:10)

Server already running. Player spawned at glass platform. Chat clear.

> "This is Minecraft. But the world I'm about to load is not procedural — it's
> a real dataset. Every block is a data point. Every beacon is a cluster mean.
> I'm going to fly through my data."

## /upload (0:10–0:25)

```
/upload C:\Users\Nikil PS\blocks_space\samples\iris.csv csv
```

Wait ~1s. ~150 blocks pop in across the void.

> "150 rows of the iris dataset. Python pipeline standardizes, runs UMAP to
> three dimensions, KMeans++ picks k by silhouette, IsolationForest flags
> outliers. The plugin places one block per row. Colors are clusters; glass
> blocks are outliers."

## /center + hover (0:25–0:50)

```
/center
```

Camera teleports above the centroid sculk catalyst. Look at one of the colored
points. Sidebar HUD appears with Cluster, Dist→mean, Outlier score, Cluster
size. Orange dust line traces from block to its beacon. Look at a beacon —
sidebar switches to Dist→centroid; end-rod line shoots to the catalyst.

> "Hover anything for live distance metrics. Hovering a point shows its cluster
> and outlier score; the line connects it to its cluster mean. The mean shows
> distance to the global centroid — that catalyst block is the centroid."

## /overview (0:50–1:05)

```
/overview
```

Gemini 2.5 Flash Lite returns a two-paragraph summary. It broadcasts in chat.

> "Now Gemini reads the dataset summary and explains it in plain English.
> Two clusters of iris, distinguished mostly by petal length and width, with
> nine outliers."

## /visualize (1:05–1:25)

```
/visualize highlight outliers with flame particles
```

Wait. ~8 blocks erupt with flame for 5 seconds.

```
/visualize recolor cluster 0 to emerald blocks
```

Half the cloud turns green.

> "And I can drive the visualization in English. Gemini outputs a strict JSON
> tool spec — recolor, highlight, hide — never raw commands. The plugin
> validates against an allow-list before touching the world."

## Close (1:25–1:30)

```
/reset
```

World clears.

> "Stack Unknown: data exploration you can fly through."

---

## Pre-demo checklist

- [ ] `docker compose up -d` and wait for "Done ("
- [ ] `python -u -m engine.ws_server` running (check `netstat -ano | findstr :8765`)
- [ ] Op self: `docker exec stack-unknown-mc rcon-cli "op Knightbones8283"`
- [ ] In-game: `/gamemode spectator`, F1 to hide HUD overlays, F5 for camera
- [ ] Window: 1080p+, GUI scale 3 for HUD readability on stream
- [ ] OBS scene ready, mic test, audio levels checked
- [ ] Three CSV paths in a sticky note (iris, digits_1k, wine — wine and digits
      are backup if iris hits a Gemini rate limit and you need a re-run with a
      cache-warm dataset)
- [ ] Pre-warm Gemini cache: run `/overview` once before the demo so the second
      call hits the LRU cache and returns instantly
- [ ] Test `/visualize` queries twice so the cache hits during the live run

## Backup if something breaks

| Failure | Reaction |
|---|---|
| Gemini 429 | Fall back to `/visualize highlight cluster 0` — local regex parser still works |
| WS disconnect mid-demo | Keep talking; the plugin reconnects on backoff |
| `/upload` slow | While it loads, narrate the architecture diagram |
| Block flood TPS drop | Pre-run the upload off-camera, cut to fresh world |
