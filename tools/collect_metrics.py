#!/usr/bin/env python3
"""Collect public aggregate GitHub metrics only; never requests private traffic endpoints."""
from pathlib import Path
import argparse,datetime,json,urllib.request
p=argparse.ArgumentParser();p.add_argument('--repo',default='imthedark-dark/motion2video-pro');p.add_argument('--output',default='docs/metrics.json');a=p.parse_args()
def api(endpoint):
 request=urllib.request.Request('https://api.github.com/'+endpoint,headers={'Accept':'application/vnd.github+json','User-Agent':'Motion2Video-public-metrics'})
 with urllib.request.urlopen(request,timeout=30) as r:return json.load(r)
repo=api('repos/'+a.repo);releases=api('repos/'+a.repo+'/releases?per_page=100')
report={'repository':a.repo,'collected_at_utc':datetime.datetime.now(datetime.timezone.utc).isoformat(),'stars':repo['stargazers_count'],'forks':repo['forks_count'],'subscribers':repo.get('subscribers_count'),'release_assets':[],'release_asset_downloads':0,'direct_raw_downloads':None,'active_users':None,'private_traffic':'Not requested or published.','notes':['Public API snapshot; counts do not establish unique users or successful renders.','GitHub does not expose raw.githubusercontent.com download counts here; direct APK downloads are unknown.','The app has no usage telemetry; active users and real-world adoption are unknown.']}
for release in releases:
 for asset in release.get('assets',[]):
  report['release_assets'].append({'tag':release['tag_name'],'name':asset['name'],'downloads':asset['download_count']});report['release_asset_downloads']+=asset['download_count']
out=Path(a.output);out.parent.mkdir(parents=True,exist_ok=True);out.write_text(json.dumps(report,ensure_ascii=False,indent=2)+'\n');print(json.dumps(report,ensure_ascii=False,indent=2))
