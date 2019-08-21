workflow "Build And Release" {
  on = "push"
  resolves = ["Releases"]
}

action "GitHub Action for Maven" {
  uses = "LucaFeger/action-maven-cli@765e218a50f02a12a7596dc9e7321fc385888a27"
  args = "compile hpi:hpi"
}

action "Releases" {
  uses = "fnkr/github-action-ghr@v1"
  needs = ["GitHub Action for Maven"]
  secrets = ["GITHUB_TOKEN"]
}
